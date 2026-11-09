<template>
  <div class="login-container">
    <!-- 装饰光斑 -->
    <div class="glow glow-1"></div>
    <div class="glow glow-2"></div>
    <div class="glow glow-3"></div>

    <div class="login-card">
      <div class="brand">
        <span class="brand-badge"><i class="el-icon-cpu"></i></span>
      </div>
      <h3 class="title">码匠 · CodeGen</h3>
      <p class="subtitle">带质量护栏的小型低代码平台</p>

      <el-form
        ref="loginForm"
        :model="loginForm"
        :rules="loginRules"
        class="login-form"
        label-position="left"
      >
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="用户名"
            prefix-icon="el-icon-user"
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="密码"
            prefix-icon="el-icon-lock"
            show-password
            @keyup.enter.native="handleLogin"
          />
        </el-form-item>

        <el-button
          :loading="loading"
          type="primary"
          class="login-btn"
          @click.native.prevent="handleLogin"
        >
          登 录
        </el-button>

        <div class="login-tip">
          <i class="el-icon-info"></i> 默认账号：admin / admin123
        </div>
      </el-form>
    </div>

    <div class="footer-note">CodeGen Platform · Vue2 + Element UI</div>
  </div>
</template>

<script>
export default {
  name: 'LoginPage',
  data() {
    return {
      loading: false,
      loginForm: {
        username: 'admin',
        password: 'admin123'
      },
      loginRules: {
        username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
        password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
      }
    }
  },
  methods: {
    handleLogin() {
      this.$refs.loginForm.validate(valid => {
        if (!valid) return
        this.loading = true
        this.$store.dispatch('user/login', this.loginForm)
          .then(() => {
            this.$router.push(this.$route.query.redirect || '/gen/table')
          })
          .catch(() => {
            // 具体错误信息（如"用户名或密码错误"）已由 request 拦截器统一提示，这里不重复弹
          })
          .finally(() => {
            this.loading = false
          })
      })
    }
  }
}
</script>

<style scoped>
/* 登录页不提供任何主题切换入口：它的皮肤来自「已保存的主题偏好」，
   由 public/index.html 的内联脚本在首帧注入，或由登录成功后 applyTheme 更新。 */
.login-container {
  position: relative;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: linear-gradient(
    135deg,
    var(--cg-hero-from) 0%,
    var(--cg-hero-via) 45%,
    var(--cg-hero-to) 100%
  );
  overflow: hidden;
}

/* 装饰光斑 */
.glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(90px);
  opacity: 0.45;
  pointer-events: none;
}
.glow-1 {
  width: 420px;
  height: 420px;
  background: var(--cg-glow-1);
  top: -120px;
  left: -100px;
}
.glow-2 {
  width: 360px;
  height: 360px;
  background: var(--cg-glow-2);
  bottom: -140px;
  right: -80px;
}
.glow-3 {
  width: 220px;
  height: 220px;
  background: var(--cg-glow-3);
  top: 40%;
  left: 62%;
}

.login-card {
  position: relative;
  z-index: 1;
  width: 400px;
  padding: 40px 40px 28px;
  background: var(--cg-bg-elevated);
  border-radius: var(--cg-radius-lg);
  border: 1px solid var(--cg-border-light);
  box-shadow: var(--cg-shadow-login);
  backdrop-filter: blur(8px);
}

.brand {
  display: flex;
  justify-content: center;
  margin-bottom: 14px;
}

.brand-badge {
  width: 56px;
  height: 56px;
  border-radius: var(--cg-radius-badge);
  background: linear-gradient(135deg, var(--cg-primary-solid), var(--cg-primary-active));
  display: inline-flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 10px 24px var(--cg-primary-glow);
}

.brand-badge i {
  font-size: 32px;
  color: #fff;
}

.title {
  margin: 0;
  text-align: center;
  font-size: 22px;
  font-weight: 700;
  color: var(--cg-text);
}

.subtitle {
  margin: 8px 0 26px;
  text-align: center;
  font-size: 13px;
  color: var(--cg-text-secondary);
}

.login-form ::v-deep .el-input__inner {
  height: var(--cg-control-h);
  line-height: var(--cg-control-h);
  border-radius: var(--cg-radius-input);
  background: var(--cg-input-bg);
  border-color: var(--cg-border);
  color: var(--cg-text);
}

.login-form ::v-deep .el-input__inner:focus {
  background: var(--cg-input-bg-focus);
  border-color: var(--cg-primary);
  box-shadow: 0 0 0 3px var(--cg-primary-ring);
}

.login-btn {
  width: 100%;
  height: var(--cg-control-h);
  margin-top: 6px;
  font-size: 16px;
  letter-spacing: 4px;
  border: none;
  border-radius: var(--cg-radius-btn);
  background: linear-gradient(90deg, var(--cg-primary-solid), var(--cg-primary-active));
  box-shadow: var(--cg-shadow-btn);
}

.login-btn:hover,
.login-btn:focus {
  background: linear-gradient(90deg, var(--cg-primary-solid), var(--cg-primary-active));
  box-shadow: 0 10px 26px var(--cg-primary-glow);
}

.login-tip {
  margin-top: 16px;
  text-align: center;
  font-size: 12px;
  color: var(--cg-text-placeholder);
}

.footer-note {
  position: relative;
  z-index: 1;
  margin-top: 26px;
  font-size: 12px;
  color: var(--cg-hero-footer);
  letter-spacing: 1px;
}
</style>
