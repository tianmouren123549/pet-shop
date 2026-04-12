<template>
  <div class="admin-placeholder">
    <div class="card">
      <h2>功能建设中</h2>
      <p>该管理模块正在升级，请稍后再试或通过其他已开放入口完成操作。</p>
      <p class="sub">如需处理店铺商品与订单，请使用商家工作台相关菜单。</p>
    </div>
  </div>
</template>

<style scoped>
.admin-placeholder {
  min-height: calc(100vh - 220px);
  display: flex;
  align-items: center;
  justify-content: center;
}
.card {
  width: 100%;
  max-width: 720px;
  background: #f9fbfe;
  border: 1px solid #d9e2ee;
  border-radius: 4px;
  padding: 34px;
  text-align: center;
}
h2 {
  font-size: 30px;
  color: #0f1f36;
  margin-bottom: 10px;
}
p {
  color: #5f7087;
  font-size: 14px;
  line-height: 1.8;
}
.sub {
  margin-top: 8px;
  color: #8898ad;
  font-size: 12px;
}
</style>

