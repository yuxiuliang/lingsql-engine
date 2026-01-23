<script setup lang="ts">
import {onMounted, reactive, ref} from 'vue';
import {useRouter} from 'vue-router';
import {ElMessage, ElLoading} from 'element-plus';
import {useAuthStore} from '@/store/auth';

/**
 * 画布背景引用。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
const canvasRef = ref<HTMLCanvasElement | null>(null);

/**
 * 登录表单状态。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
const form = reactive({
  username: '',
  tenantId: '',
  token: ''
});

/**
 * 记住租户ID开关。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
const rememberTenant = ref(false);

const router = useRouter();
const authStore = useAuthStore();

/**
 * 初始化租户记忆状态。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
const initRememberTenant = () => {
  const remember = localStorage.getItem('rememberTenant') === 'true';
  rememberTenant.value = remember;
  if (remember) {
    form.tenantId = localStorage.getItem('tenantId') || '';
  }
};

/**
 * 保存租户记忆状态。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
const persistRememberTenant = () => {
  localStorage.setItem('rememberTenant', String(rememberTenant.value));
  if (!rememberTenant.value) {
    localStorage.removeItem('tenantId');
  }
};

/**
 * 提交登录表单并写入缓存。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
const handleLogin = async () => {
  if (!form.username.trim()) {
    ElMessage.warning('请输入用户名');
    return;
  }
  if (!form.tenantId.trim()) {
    ElMessage.warning('请输入租户ID');
    return;
  }
  if (!form.token.trim()) {
    ElMessage.warning('请输入令牌');
    return;
  }
  persistRememberTenant();
  const loadingService = ElLoading.service({
    fullscreen: true,
    text: '正在登录...',
    background: 'rgba(15, 23, 42, 0.45)'
  });
  try {
    authStore.login(form.token.trim(), form.tenantId.trim(), form.username.trim());
    ElMessage.success('登录成功');
    await router.push('/chat');
  } finally {
    loadingService.close();
  }
};

/**
 * 粒子对象定义。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
interface Particle {
  x: number;
  y: number;
  r: number;
  vx: number;
  vy: number;
  alpha: number;
}

/**
 * 流星对象定义。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
interface Meteor {
  x: number;
  y: number;
  len: number;
  speed: number;
  alpha: number;
}

/**
 * 初始化科技感背景动画。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
const initCanvas = () => {
  const canvas = canvasRef.value;
  if (!canvas) {
    return;
  }
  const ctx = canvas.getContext('2d');
  if (!ctx) {
    return;
  }
  const particles: Particle[] = [];
  const meteors: Meteor[] = [];
  let width = 0;
  let height = 0;

  const resize = () => {
    width = canvas.clientWidth;
    height = canvas.clientHeight;
    canvas.width = width * window.devicePixelRatio;
    canvas.height = height * window.devicePixelRatio;
    ctx.scale(window.devicePixelRatio, window.devicePixelRatio);
  };

  const createParticles = () => {
    particles.length = 0;
    const count = Math.min(120, Math.floor(width / 8));
    for (let i = 0; i < count; i += 1) {
      particles.push({
        x: Math.random() * width,
        y: Math.random() * height,
        r: Math.random() * 1.6 + 0.6,
        vx: (Math.random() - 0.5) * 0.3,
        vy: (Math.random() - 0.5) * 0.3,
        alpha: Math.random() * 0.5 + 0.3
      });
    }
  };

  const spawnMeteor = () => {
    if (meteors.length > 6) {
      return;
    }
    if (Math.random() < 0.03) {
      meteors.push({
        x: Math.random() * width,
        y: -20,
        len: Math.random() * 120 + 80,
        speed: Math.random() * 6 + 4,
        alpha: Math.random() * 0.4 + 0.4
      });
    }
  };

  const render = () => {
    ctx.clearRect(0, 0, width, height);
    ctx.fillStyle = '#0b1220';
    ctx.fillRect(0, 0, width, height);

    ctx.save();
    ctx.globalCompositeOperation = 'lighter';
    particles.forEach((p) => {
      ctx.beginPath();
      ctx.fillStyle = `rgba(99, 102, 241, ${p.alpha})`;
      ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2);
      ctx.fill();
      p.x += p.vx;
      p.y += p.vy;
      if (p.x < 0 || p.x > width) {
        p.vx *= -1;
      }
      if (p.y < 0 || p.y > height) {
        p.vy *= -1;
      }
    });
    ctx.restore();

    meteors.forEach((m, index) => {
      ctx.beginPath();
      ctx.strokeStyle = `rgba(148, 163, 255, ${m.alpha})`;
      ctx.lineWidth = 2;
      ctx.moveTo(m.x, m.y);
      ctx.lineTo(m.x + m.len, m.y + m.len * 0.4);
      ctx.stroke();
      m.x += m.speed;
      m.y += m.speed * 0.4;
      if (m.y > height + 60) {
        meteors.splice(index, 1);
      }
    });

    spawnMeteor();
    requestAnimationFrame(render);
  };

  resize();
  createParticles();
  render();

  window.addEventListener('resize', () => {
    resize();
    createParticles();
  });
};

onMounted(() => {
  initRememberTenant();
  initCanvas();
});
</script>

<template>
  <div class="login-page">
    <canvas ref="canvasRef" class="login-canvas"></canvas>
    <div class="sql-texture">
      SELECT id, name, amount FROM orders WHERE tenant_id = ?;
      CREATE TABLE t_lingsql_engine_ai_model_config (id BIGINT, model_name VARCHAR(64));
      SELECT sum(order_amount) AS total, count(*) AS cnt FROM orders GROUP BY day;
    </div>
    <div class="login-container">
      <div class="login-card">
        <div class="logo-area">
          <svg class="logo-icon" viewBox="0 0 64 64" aria-hidden="true">
            <defs>
              <linearGradient id="logoGradient" x1="0%" y1="0%" x2="100%" y2="100%">
                <stop offset="0%" stop-color="#409EFF"/>
                <stop offset="100%" stop-color="#67C23A"/>
              </linearGradient>
            </defs>
            <circle cx="32" cy="32" r="28" fill="url(#logoGradient)"/>
            <path
              d="M20 34c6-10 18-14 26-10-2 8-8 14-16 16-4 1-7 1-10-6z"
              fill="#0f172a"
              opacity="0.7"
            />
            <circle cx="26" cy="26" r="4" fill="#0f172a" opacity="0.8"/>
          </svg>
          <div class="logo-text">语数 LingSQL</div>
        </div>
        <div class="card-body">
          <div class="card-left">
            <div class="slogan-title">语数：让业务懂数据，让数据开口说话</div>
            <div class="slogan-desc">
              多租户 Text-to-SQL 智能引擎，助力业务快速洞察与决策。
            </div>
            <div class="slogan-metrics">
              <div class="metric">
                <div class="metric-value">99.9%</div>
                <div class="metric-label">查询可用性</div>
              </div>
              <div class="metric">
                <div class="metric-value">100+</div>
                <div class="metric-label">企业租户</div>
              </div>
              <div class="metric">
                <div class="metric-value">秒级</div>
                <div class="metric-label">洞察响应</div>
              </div>
            </div>
          </div>
          <div class="card-right">
            <el-form label-position="top">
              <el-form-item label="用户名">
                <el-input v-model="form.username" placeholder="请输入用户名" class="form-input"/>
              </el-form-item>
              <el-form-item label="租户ID">
                <el-input v-model="form.tenantId" placeholder="请输入租户ID" class="form-input"/>
              </el-form-item>
              <el-form-item label="访问令牌">
                <el-input v-model="form.token" placeholder="请输入令牌" show-password class="form-input"/>
              </el-form-item>
              <div class="form-options">
                <el-checkbox v-model="rememberTenant">记住租户 ID</el-checkbox>
              </div>
              <el-button type="primary" class="login-button" @click="handleLogin">登录</el-button>
            </el-form>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  position: relative;
  height: 100vh;
  overflow: hidden;
  background: #0b1220;
}

.login-canvas {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  z-index: 0;
}

.sql-texture {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  font-size: 14px;
  line-height: 2;
  color: rgba(148, 163, 184, 0.18);
  backdrop-filter: blur(6px);
  padding: 40px;
  z-index: 1;
  pointer-events: none;
  white-space: pre-wrap;
}

.login-container {
  position: relative;
  z-index: 2;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.login-card {
  width: 100%;
  max-width: 980px;
  background: rgba(15, 23, 42, 0.5);
  border-radius: 24px;
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.5);
  backdrop-filter: blur(12px);
  padding: 28px 30px;
  color: #f8fafc;
}

.logo-area {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 18px;
}

.logo-icon {
  width: 44px;
  height: 44px;
}

.logo-text {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 1px;
}

.card-body {
  display: flex;
  gap: 24px;
}

.card-left {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding-right: 12px;
}

.slogan-title {
  font-size: 20px;
  font-weight: 600;
}

.slogan-desc {
  font-size: 14px;
  color: rgba(226, 232, 240, 0.75);
  line-height: 1.6;
}

.slogan-metrics {
  display: flex;
  gap: 16px;
  margin-top: 10px;
}

.metric {
  background: rgba(30, 41, 59, 0.55);
  padding: 12px 14px;
  border-radius: 12px;
  min-width: 90px;
  box-shadow: 0 12px 24px rgba(15, 23, 42, 0.25);
}

.metric-value {
  font-size: 18px;
  font-weight: 700;
  color: #38bdf8;
}

.metric-label {
  font-size: 12px;
  color: rgba(226, 232, 240, 0.75);
  margin-top: 4px;
}

.card-right {
  flex: 0 0 320px;
  background: rgba(15, 23, 42, 0.4);
  border-radius: 18px;
  padding: 18px;
  box-shadow: inset 0 0 0 1px rgba(148, 163, 184, 0.08);
}

.form-input :deep(.el-input__wrapper) {
  border: none;
  background: rgba(255, 255, 255, 0.06);
  box-shadow: inset 0 -1px 0 rgba(148, 163, 184, 0.35);
  transition: box-shadow 0.3s ease, background 0.3s ease;
}

.form-input :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 6px rgba(64, 158, 255, 0.15);
  background: rgba(255, 255, 255, 0.12);
}

.form-input :deep(.el-input__inner) {
  color: #f8fafc;
}

.form-options {
  display: flex;
  justify-content: flex-start;
  margin-bottom: 16px;
}

.login-button {
  width: 100%;
  border: none;
  background: linear-gradient(90deg, #409eff 0%, #67c23a 100%);
  box-shadow: 0 12px 24px rgba(64, 158, 255, 0.35);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.login-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 16px 30px rgba(64, 158, 255, 0.5);
}

@media (max-width: 900px) {
  .login-card {
    padding: 20px;
  }

  .card-body {
    flex-direction: column;
  }

  .card-right {
    flex: 1;
    width: 100%;
  }

  .slogan-metrics {
    flex-wrap: wrap;
  }
}

@media (max-width: 600px) {
  .login-card {
    padding: 16px;
  }

  .logo-text {
    font-size: 18px;
  }

  .slogan-title {
    font-size: 16px;
  }
}
</style>
