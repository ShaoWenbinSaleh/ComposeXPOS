// Terser repeatedly stalls on the generated CashRegister Compose/JS bundle.
// The Pages build is a functional preview; favor a deterministic artifact over
// a smaller bundle until the Kotlin/JS toolchain can minify it reliably.
config.optimization = config.optimization || {};
config.optimization.minimize = false;
