<template>
  <main class="login-page" data-product="p2" data-testid="product-login">
    <section class="nx-card login-card" aria-labelledby="login-title">
      <header class="login-brand">
        <h1 id="login-title">星枢 NexusOne</h1>
        <p>登录进入工作台</p>
      </header>
      <div class="login-actions">
        <button class="nx-btn nx-btn--primary" type="button" disabled>登录</button>
      </div>
      <div class="login-feedback" aria-live="polite" data-testid="login-feedback" />
      <footer class="login-secondary" data-testid="login-secondary" />
    </section>
  </main>
</template>

<style scoped>
.login-page {
  min-height: 100dvh;
  display: grid;
  place-items: center;
  padding: var(--nx-space-6);
  background: var(--nx-bg-page);
}
.login-card {
  width: 100%;
  max-width: var(--nx-auth-panel-width);
  padding: var(--nx-space-8);
  display: grid;
  gap: var(--nx-space-6);
}
.login-brand { text-align: center; }
.login-brand h1 {
  margin: var(--nx-space-0);
  font-size: var(--nx-font-size-page-title);
  color: var(--nx-text-primary);
}
.login-brand p {
  margin: var(--nx-space-2) var(--nx-space-0) var(--nx-space-0);
  font-size: var(--nx-font-size-md);
  color: var(--nx-text-secondary);
}
.login-actions { display: grid; gap: var(--nx-space-3); }
.login-secondary { text-align: center; font-size: var(--nx-font-size-md); }
.login-feedback:empty, .login-secondary:empty { display: none; }
</style>
