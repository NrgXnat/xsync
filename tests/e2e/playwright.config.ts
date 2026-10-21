import { defineConfig } from '@playwright/test';
import * as path from 'path';
import * as dotenv from 'dotenv';

dotenv.config({ path: path.resolve(__dirname, '.env') });

const BASE_URL = process.env.XNAT_URL || 'http://localhost:8080';

export default defineConfig({
    testDir: './tests',
    timeout: 60_000,
    expect: { timeout: 10_000 },

    // Every spec in this suite mutates site-wide XSync preferences (whitelist
    // toggle, connection types, project blacklist). Two of them running at once
    // would interleave writes and produce results that do not reflect either
    // test. Sequential execution is a correctness requirement here, not a
    // performance choice.
    fullyParallel: false,
    workers: 1,
    retries: 0,

    reporter: [['html', { open: 'never' }], ['list']],
    use: {
        baseURL: BASE_URL,
        ignoreHTTPSErrors: true,
        screenshot: 'only-on-failure',
        trace: 'retain-on-failure',
        actionTimeout: 15_000,
        navigationTimeout: 30_000,
    },
    projects: [
        {
            name: 'setup',
            testMatch: /global-setup\.ts$/,
        },
        {
            name: 'admin',
            // The lookbehind keeps nonadmin.*.spec.ts out of this project;
            // a bare /admin\./ matches it too.
            testMatch: /(?<![a-z])admin\..*\.spec\.ts$/,
            dependencies: ['setup'],
            use: { storageState: path.resolve(__dirname, '.auth/admin.json') },
        },
        {
            name: 'nonadmin',
            testMatch: /nonadmin\..*\.spec\.ts$/,
            dependencies: ['setup'],
            use: { storageState: path.resolve(__dirname, '.auth/nonadmin.json') },
        },
    ],
});
