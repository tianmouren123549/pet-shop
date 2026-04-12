import { reactive } from 'vue'

/**
 * 全局提示弹层状态（替代浏览器 alert，避免「localhost 显示」等原生样式）。
 */
export const appMessageState = reactive({
  /** 是否显示 */
  open: false,
  /** 标题 */
  title: '提示',
  /** 正文（支持 \\n 换行） */
  body: '',
})

/**
 * 打开全局提示弹层。
 * @param {string} body 正文
 * @param {string} [title='提示'] 标题
 */
export function showAppMessage(body, title = '提示') {
  appMessageState.body = String(body ?? '')
  appMessageState.title = title || '提示'
  appMessageState.open = true
}

/**
 * 关闭全局提示弹层。
 */
export function hideAppMessage() {
  appMessageState.open = false
}
