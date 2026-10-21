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
      <h3 class="title">CodeGen 代码生成平台</h3>
      <p class="subtitle">多项目代码生成 · 模板管理 · 生成台账</p>

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
.login-container {
  position: relative;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #16224a 0%, #1d3461 45%, #2b57d9 100%);
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
  background: #4d7cfe;
  top: -120px;
  left: -100px;
}
.glow-2 {
  width: 360px;
  height: 360px;
  background: #7ab8ff;
  bottom: -140px;
  right: -80px;
}
.glow-3 {
  width: 220px;
  height: 220px;
  background: #9d7bff;
  top: 40%;
  left: 62%;
}

.login-card {
  position: relative;
  z-index: 1;
  width: 400px;
  padding: 40px 40px 28px;
  background: rgba(255, 255, 255, 0.98);
  border-radius: 16px;
  box-shadow: 0 24px 64px rgba(6, 14, 40, 0.45);
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
  border-radius: 16px;
  background: linear-gradient(135deg, #4d7cfe, #2b57d9);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 10px 24px rgba(61, 109, 245, 0.45);
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
  color: #1f2d50;
}

.subtitle {
  margin: 8px 0 26px;
  text-align: center;
  font-size: 13px;
  color: #8a94ad;
}

.login-form ::v-deep .el-input__inner {
  height: 44px;
  line-height: 44px;
  border-radius: 8px;
  background: #f5f7fc;
  border-color: #e6ebf5;
}

.login-form ::v-deep .el-input__inner:focus {
  background: #fff;
  border-color: #3d6df5;
}

.login-btn {
  width: 100%;
  height: 44px;
  margin-top: 6px;
  font-size: 16px;
  letter-spacing: 4px;
  border: none;
  border-radius: 8px;
  background: linear-gradient(90deg, #4d7cfe, #2b57d9);
  box-shadow: 0 8px 20px rgba(61, 109, 245, 0.35);
}

.login-btn:hover,
.login-btn:focus {
  background: linear-gradient(90deg, #3d6df5, #234ec4);
  box-shadow: 0 10px 26px rgba(61, 109, 245, 0.5);
}

.login-tip {
  margin-top: 16px;
  text-align: center;
  font-size: 12px;
  color: #98a2bb;
}

.footer-note {
  position: relative;
  z-index: 1;
  margin-top: 26px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.55);
  letter-spacing: 1px;
}
</style>
