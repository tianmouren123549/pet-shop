<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../utils/request'

const router = useRouter()
const cartItems = ref([])
const userId = ref(Number(localStorage.getItem('userId') || 0))
const loading = ref(false)
const errorMsg = ref('')

onMounted(async () => {
  if (!userId.value) {
    alert('请先登录')
    router.push('/login')
    return
  }
  await loadCart()
})

async function loadCart() {
  loading.value = true
  errorMsg.value = ''
  const res = await api.getCart(userId.value)
  if (res.code === 200) {
    cartItems.value = res.data
  } else {
    errorMsg.value = res.message || '购物车加载失败'
    cartItems.value = []
  }
  loading.value = false
}

async function updateQuantity(cartId, quantity) {
  if (quantity < 0) return
  
  const res = await api.updateCart({ cartId, quantity })
  if (res.code === 200) {
    await loadCart()
  } else {
    alert(res.message || '更新失败')
  }
}

async function removeItem(cartId) {
  if (!confirm('确定要删除该商品吗？')) return
  
  const res = await api.removeFromCart(cartId)
  if (res.code === 200) {
    await loadCart()
  } else {
    alert(res.message || '删除失败')
  }
}

async function checkout() {
  if (!cartItems.value.length) return
  const ok = confirm('确认结算并生成订单吗？（Mock 模式）')
  if (!ok) return
  const res = await api.userCreateOrderFromCart({ userId: userId.value })
  if (res.code === 200) {
    alert('下单成功')
    router.push(`/order/${res.data.orderId}`)
  } else {
    alert(res.message || '下单失败')
  }
}

const totalAmount = computed(() => {
  return cartItems.value.reduce((sum, item) => sum + Number(item.subtotal), 0).toFixed(2)
})

const totalItems = computed(() => {
  return cartItems.value.reduce((sum, item) => sum + item.quantity, 0)
})
</script>

<template>
  <div class="pw-page cart-page">
    <section class="pw-hero cart-hero">
      <h1 class="pw-title">我的购物车</h1>
      <p class="pw-lead">共 {{ totalItems }} 件商品</p>
    </section>

    <div v-if="loading" class="pw-state">购物车加载中...</div>

    <div v-else-if="errorMsg" class="pw-state pw-state--error">{{ errorMsg }}</div>

    <div v-else-if="cartItems.length === 0" class="pw-state pw-state--empty">
      <div class="cart-empty-title">购物车是空的</div>
      <div class="cart-empty-lead">快去选购心仪的商品吧</div>
      <button type="button" class="pw-btn pw-btn-sm" @click="$router.push('/')">去逛逛</button>
    </div>

    <div v-else class="cart-content">
      <section class="pw-section cart-table">
        <div class="table-header">
          <div class="col-product">商品信息</div>
          <div class="col-price">单价</div>
          <div class="col-quantity">数量</div>
          <div class="col-subtotal">小计</div>
          <div class="col-action">操作</div>
        </div>

        <div class="cart-items">
          <div v-for="item in cartItems" :key="item.cartId" class="cart-item">
            <div class="col-product">
              <div class="item-image">
                <img v-if="item.imageUrl" :src="item.imageUrl" class="item-img" alt="商品图片" />
                <div v-else class="item-placeholder">暂无图片</div>
              </div>
              <div class="item-details">
                <h3 class="item-title">{{ item.title }}</h3>
              </div>
            </div>
            <div class="col-price">
              <span class="price">¥{{ item.price }}</span>
            </div>
            <div class="col-quantity">
              <div class="quantity-control">
                <button
                  class="qty-btn"
                  @click="updateQuantity(item.cartId, item.quantity - 1)"
                  :disabled="item.quantity <= 0"
                >
                  −
                </button>
                <input
                  type="text"
                  class="qty-input"
                  :value="item.quantity"
                  readonly
                />
                <button
                  class="qty-btn"
                  @click="updateQuantity(item.cartId, item.quantity + 1)"
                >
                  +
                </button>
              </div>
            </div>
            <div class="col-subtotal">
              <span class="subtotal">¥{{ item.subtotal }}</span>
            </div>
            <div class="col-action">
              <button type="button" class="remove-btn" @click="removeItem(item.cartId)">
                删除
              </button>
            </div>
          </div>
        </div>
      </section>

      <section class="pw-section cart-summary">
        <div class="summary-content">
          <div class="summary-row">
            <span class="summary-label">商品总数：</span>
            <span class="summary-value">{{ totalItems }} 件</span>
          </div>
          <div class="summary-row total-row">
            <span class="summary-label">应付总额：</span>
            <span class="total-amount">¥{{ totalAmount }}</span>
          </div>
          <button type="button" class="pw-btn checkout-btn" @click="checkout">立即结算</button>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.cart-page {
  width: 100%;
  margin: 0 auto;
}

.cart-hero {
  margin-bottom: 14px;
}

.cart-empty-title {
  font-size: 14px;
  font-weight: 800;
  color: #131e30;
  margin-bottom: 6px;
}

.cart-empty-lead {
  font-size: 12px;
  color: #6b7b91;
  margin-bottom: 14px;
  text-align: center;
}

.cart-content {
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: 16px;
}

.cart-table {
  background: #f9fbfe;
  border-radius: 2px;
  overflow: hidden;
  border: 1px solid #dce4ef;
}

.table-header {
  display: grid;
  grid-template-columns: 2fr 1fr 1.2fr 1fr 0.8fr;
  padding: 16px 20px;
  background: #f1f4f8;
  font-weight: 500;
  color: #666;
  font-size: 14px;
  border-bottom: 1px solid #dce4ef;
}

.cart-items {
  max-height: 600px;
  overflow-y: auto;
}

.cart-item {
  display: grid;
  grid-template-columns: 2fr 1fr 1.2fr 1fr 0.8fr;
  padding: 20px;
  border-bottom: 1px solid #f0f0f0;
  align-items: center;
  transition: background 0.2s;
}

.cart-item:hover {
  background: #f4f7fb;
}

.col-product {
  display: flex;
  align-items: center;
  gap: 12px;
}

.item-image {
  width: 70px;
  height: 70px;
  background: #fafafa;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #bbb;
  flex-shrink: 0;
  border: 1px solid #e8e8e8;
}

.item-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  border-radius: 4px;
}

.item-placeholder {
  font-size: 12px;
  color: #bbb;
}

.item-details {
  flex: 1;
}

.item-title {
  font-size: 14px;
  color: #333;
  font-weight: 400;
  line-height: 1.5;
}

.col-price .price {
  font-size: 16px;
  color: #333;
}

.quantity-control {
  display: flex;
  align-items: center;
  gap: 0;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  overflow: hidden;
  width: fit-content;
}

.qty-btn {
  width: 32px;
  height: 32px;
  border: none;
  background: white;
  cursor: pointer;
  font-size: 16px;
  color: #666;
  transition: all 0.2s;
}

.qty-btn:hover:not(:disabled) {
  background: #f5f5f5;
  color: #1890ff;
}

.qty-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.qty-input {
  width: 50px;
  height: 32px;
  border: none;
  border-left: 1px solid #d9d9d9;
  border-right: 1px solid #d9d9d9;
  text-align: center;
  font-size: 14px;
}

.col-subtotal .subtotal {
  font-size: 18px;
  color: #ff4d4f;
  font-weight: 500;
}

.remove-btn {
  padding: 6px 12px;
  background: white;
  color: #ff4d4f;
  border: 1px solid #ff4d4f;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
  transition: all 0.2s;
}

.remove-btn:hover {
  background: #ff4d4f;
  color: white;
}

.cart-summary {
  background: #f9fbfe;
  border-radius: 2px;
  padding: 24px;
  border: 1px solid #dce4ef;
  height: fit-content;
  position: sticky;
  top: 80px;
}

.summary-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.summary-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  font-size: 14px;
}

.summary-label {
  color: #666;
}

.summary-value {
  color: #333;
}

.total-row {
  border-top: 1px solid #e8e8e8;
  padding-top: 16px;
  margin-top: 8px;
}

.total-row .summary-label {
  font-size: 16px;
  color: #333;
  font-weight: 500;
}

.total-amount {
  font-size: 28px;
  color: #ff4d4f;
  font-weight: 500;
}

.checkout-btn {
  width: 100%;
  padding: 12px;
  background: #0b1630;
  color: #f3f7ff;
  border: none;
  border-radius: 4px;
  font-size: 16px;
  cursor: pointer;
  transition: all 0.2s;
  margin-top: 8px;
}

.checkout-btn:hover {
  background: #162b4d;
}
</style>
