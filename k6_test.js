 import http from 'k6/http';
import { check, sleep } from 'k6';

// Konfigurasi K6
export const options = {
    // Skenario Load Testing (Ramp-up, Sustain, Ramp-down)
    stages: [
        { duration: '10s', target: 20 }, // Naik bertahap ke 20 Virtual Users (VUs) dalam 10 detik
        { duration: '30s', target: 20 }, // Tahan beban di 20 VUs selama 30 detik
        { duration: '10s', target: 0 },  // Turun perlahan ke 0 VUs dalam 10 detik
    ],
    thresholds: {
        http_req_duration: ['p(95)<2000'], // 95% request harus selesai di bawah 2 detik
        http_req_failed: ['rate<0.01'],    // Tingkat error (gagal) harus di bawah 1%
    },
};


const BASE_URL = 'http://localhost:8080/api';
const KEYCLOAK_URL = 'http://localhost:8081/realms/PaymentGatewayRealm/protocol/openid-connect/token';

// Helper function untuk generate ID sesuai pattern yang diminta:
// "PREFIX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
function generateRef(prefix) {
    // Generate 8 karakter random hex uppercase untuk simulasi substring UUID
    const chars = '0123456789ABCDEF';
    let shortUuid = '';
    for (let i = 0; i < 8; i++) {
        shortUuid += chars.charAt(Math.floor(Math.random() * chars.length));
    }
    return prefix + shortUuid;
}

// Setup block dijalankan SATU KALI di awal sebelum iterasi VU dimulai.
// Best practice: Ambil JWT token di sini agar dinamis dan tidak expired.
export function setup() {
    const payload = {
        client_id: 'payment-gateway-client',
        client_secret: 'J8lGF7B4byHLgUJVU3w3a8Af802p2q6I',
        grant_type: 'password',
        username: 'payment-user',
        password: 'password123',
        scope: 'openid'
    };

    const res = http.post(KEYCLOAK_URL, payload);
    
    // Validasi token berhasil diambil
    check(res, { 'Token fetched successfully': (r) => r.status === 200 });

    return { token: res.json('access_token') };
}

// Main execution function
export default function (data) {
    const token = data.token;
    const headers = {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
    };

    console.log(`\n--- Memulai Test Iterasi ---`);

    // -------------------------------------------------------------
    // 1. Process a new Payment (Success Scenario)
    // -------------------------------------------------------------
    const successOrderId = generateRef("INV-");
    const successIdempKey = generateRef("IDEMP-");
    
    const successPayload = JSON.stringify({
        order_id: successOrderId,
        amount: 1700.00,
        currency: "IDR",
        payment_method: "VIRTUAL_ACCOUNT",
        channel: "MOBILE_BANKING"
    });

    let res = http.post(`${BASE_URL}/payments`, successPayload, {
        headers: Object.assign({}, headers, { 'Idempotency-Key': successIdempKey })
    });

    check(res, {
        '1. Success Payment - status is 200/201': (r) => r.status === 200 || r.status === 201,
        '1. Success Payment - status field is SUCCESS/PENDING': (r) => {
            const body = r.json();
            return body.data && (body.data.status === 'SUCCESS' || body.data.status === 'PENDING');
        }
    });
    // Selesai satu flow iterasi, beri jeda sedikit agar tidak membebani CPU test runner
    sleep(0.3);

    // -------------------------------------------------------------
    // 2. Process Payment (Insufficient Balance - CoreBank Failure)
    // -------------------------------------------------------------
    const cbFailOrderId = "99-" + generateRef("INV-"); // Gunakan 99 untuk men-trigger mock CoreBank gagal
    const cbFailIdempKey = generateRef("IDEMP-");
    
    const cbFailPayload = JSON.stringify({
        order_id: cbFailOrderId,
        amount: 50000.00,
        currency: "IDR",
        payment_method: "QRIS",
        channel: "ATM"
    });

    res = http.post(`${BASE_URL}/payments`, cbFailPayload, {
        headers: Object.assign({}, headers, { 'Idempotency-Key': cbFailIdempKey })
    });

    check(res, {
        '2. CoreBank Fail - status is 200': (r) => r.status === 200,
        '2. CoreBank Fail - status field is FAILED': (r) => r.json('data.status') === 'FAILED'
    });
    // Selesai satu flow iterasi, beri jeda sedikit agar tidak membebani CPU test runner
    sleep(0.3);

    // -------------------------------------------------------------
    // 3. Process Payment (Biller Failure)
    // -------------------------------------------------------------
    const billerFailOrderId = generateRef("INV-");
    const billerFailIdempKey = generateRef("IDEMP-");
    
    const billerFailPayload = JSON.stringify({
        order_id: billerFailOrderId,
        amount: 75000.00,
        currency: "IDR",
        payment_method: "FAIL", // Ini akan men-trigger mock Biller gagal
        channel: "MOBILE_BANKING"
    });

    res = http.post(`${BASE_URL}/payments`, billerFailPayload, {
        headers: Object.assign({}, headers, { 'Idempotency-Key': billerFailIdempKey })
    });

    check(res, {
        '3. Biller Fail - status is 200': (r) => r.status === 200,
        '3. Biller Fail - status field is FAILED': (r) => r.json('data.status') === 'FAILED'
    });
    // Selesai satu flow iterasi, beri jeda sedikit agar tidak membebani CPU test runner
    sleep(0.3);

    // -------------------------------------------------------------
    // 4. Process Payment (Duplicate Idempotency Key)
    // -------------------------------------------------------------
    const dupOrderId = generateRef("INV-");
    const dupIdempKey = generateRef("IDEMP-");
    
    const dupPayload = JSON.stringify({
        order_id: dupOrderId,
        amount: 10000.00,
        currency: "IDR",
        payment_method: "CREDIT_CARD",
        channel: "INTERNET_BANKING"
    });

    // Request pertama (Harusnya sukses)
    http.post(`${BASE_URL}/payments`, dupPayload, {
        headers: Object.assign({}, headers, { 'Idempotency-Key': dupIdempKey })
    });
    
    // Request kedua dengan payload yang BEDAA tapi Idempotency-Key yang SAMA (Harusnya Conflict)
    const dupPayloadChanged = JSON.stringify({
        order_id: dupOrderId + "-CHANGED",
        amount: 20000.00,
        currency: "IDR",
        payment_method: "CREDIT_CARD",
        channel: "INTERNET_BANKING"
    });

    res = http.post(`${BASE_URL}/payments`, dupPayloadChanged, {
        headers: Object.assign({}, headers, { 'Idempotency-Key': dupIdempKey })
    });

    // Kita expect existing transaction dikembalikan (200 atau 201) ATAU terjadi HTTP 409 Conflict. 
    // Bergantung pada implementasi backend idempotency nya (entah ngembaliin hasil awal atau nge-throw error).
    check(res, {
        '4. Idempotency Check - Returns early or conflicts': (r) => r.status === 200 || r.status === 201 || r.status === 409
    });
    sleep(1);

    // -------------------------------------------------------------
    // 5. Get Transaction Status
    // -------------------------------------------------------------
    res = http.get(`${BASE_URL}/payments/${successOrderId}`, {
        headers: { 'Authorization': `Bearer ${token}` }
    });

    check(res, {
        '5. Get Status - status is 200': (r) => r.status === 200,
        '5. Get Status - order_id matches': (r) => r.json('data.order_id') === successOrderId
    });
    
    console.log(`--- Iterasi Selesai ---\n`);
}
