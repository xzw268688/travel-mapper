<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import { EditorContent, useEditor } from '@tiptap/vue-3'
import { mergeAttributes, Node, type Editor as CoreEditor } from '@tiptap/core'
import StarterKit from '@tiptap/starter-kit'
import Image from '@tiptap/extension-image'
import Link from '@tiptap/extension-link'
import Placeholder from '@tiptap/extension-placeholder'
import { cancelPolish, polishStream } from '../api/ai'
import { uploadImage, uploadImageThumbnail, uploadVideo } from '../api/notes'
import { compressImage, createThumbnail } from '../utils/image'
import { contentToPreview, previewToContent } from '../utils/noteContent'
import { newRequestId } from '../utils/uuid'

const Video = Node.create({
  name: 'video',
  group: 'block',
  atom: true,
  draggable: true,
  addAttributes() {
    return {
      src: { default: null }
    }
  },
  parseHTML() {
    return [{ tag: 'video' }]
  },
  renderHTML({ HTMLAttributes }) {
    return [
      'video',
      mergeAttributes(HTMLAttributes, {
        controls: 'controls',
        preload: 'metadata',
        playsinline: 'playsinline'
      })
    ]
  }
})

const props = withDefaults(
  defineProps<{
    modelValue: string
    imageUrls?: Record<string, string>
    maxImages?: number
    maxVideos?: number
    placeholder?: string
  }>(),
  {
    imageUrls: () => ({}),
    maxImages: 20,
    maxVideos: 3,
    placeholder: '记录旅途中的见闻…'
  }
)

const emit = defineEmits<{
  (event: 'update:modelValue', value: string): void
  (event: 'image-uploaded', objectKey: string): void
  (event: 'video-uploaded', objectKey: string): void
  (event: 'error', message: string): void
}>()

const fileInput = ref<HTMLInputElement | null>(null)
const videoInput = ref<HTMLInputElement | null>(null)
const uploading = ref(false)
const keyByUrl: Record<string, string> = {}
const blobUrls: string[] = []

const polishStyle = ref('vivid')
const polishStreaming = ref(false)
const polishReady = ref(false)
const polishPreview = ref('')
const polishError = ref('')
const polishStatus = ref('')
const selectionLength = ref(0)
let polishRange: { from: number; to: number } | null = null
let polishController: AbortController | null = null
let polishRequestId = ''

const canPolish = computed(
  () => selectionLength.value > 0 && selectionLength.value <= 5000 && !polishStreaming.value
)

function registerImageUrls(urls: Record<string, string>): void {
  Object.entries(urls).forEach(([key, url]) => {
    if (url) {
      keyByUrl[url] = key
    }
  })
}
registerImageUrls(props.imageUrls)
watch(
  () => props.imageUrls,
  (urls) => registerImageUrls(urls),
  { deep: true }
)

const editor = useEditor({
  content: contentToPreview(props.modelValue, props.imageUrls),
  extensions: [
    StarterKit,
    Image.configure({ inline: false, allowBase64: false }),
    Video,
    Link.configure({ openOnClick: false, autolink: true }),
    Placeholder.configure({ placeholder: props.placeholder })
  ],
  editorProps: {
    attributes: { class: 'rich-editor-content' },
    handleDrop(view: any, event: DragEvent, _slice: any, moved: boolean): boolean {
      if (moved) {
        return false
      }
      const files = Array.from(event.dataTransfer?.files ?? []).filter((file) =>
        file.type.startsWith('image/')
      )
      if (files.length === 0) {
        return false
      }
      event.preventDefault()
      files.forEach((file) => void insertImageFile(file))
      return true
    },
    handlePaste(_view: any, event: ClipboardEvent): boolean {
      const files = Array.from(event.clipboardData?.files ?? []).filter((file) =>
        file.type.startsWith('image/')
      )
      if (files.length === 0) {
        return false
      }
      event.preventDefault()
      files.forEach((file) => void insertImageFile(file))
      return true
    }
  },
  onUpdate: ({ editor: instance }) => {
    emit('update:modelValue', previewToContent(instance.getHTML(), keyByUrl))
    syncSelection(instance)
  },
  onSelectionUpdate: ({ editor: instance }) => {
    syncSelection(instance)
  }
})

function syncSelection(instance?: CoreEditor): void {
  if (!instance) {
    selectionLength.value = 0
    return
  }
  const { from, to } = instance.state.selection
  selectionLength.value = instance.state.doc.textBetween(from, to, '\n').length
}

watch(
  () => props.modelValue,
  (value) => {
    const instance = editor.value
    if (!instance) {
      return
    }
    if (value !== previewToContent(instance.getHTML(), keyByUrl)) {
      instance.commands.setContent(contentToPreview(value, props.imageUrls), false)
    }
  }
)

onBeforeUnmount(() => {
  polishController?.abort()
  blobUrls.forEach((url) => URL.revokeObjectURL(url))
})

function imageCount(): number {
  return editor.value?.getHTML().match(/<img/g)?.length ?? 0
}

async function insertImageFile(file: File): Promise<void> {
  if (!file.type.startsWith('image/')) {
    emit('error', '请选择图片文件')
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    emit('error', '单图不能超过 5MB')
    return
  }
  if (imageCount() >= props.maxImages) {
    emit('error', `最多插入 ${props.maxImages} 张图片`)
    return
  }
  uploading.value = true
  try {
    const compressed = await compressImage(file)
    const objectKey = await uploadImage(compressed)
    try {
      const thumb = await createThumbnail(compressed)
      await uploadImageThumbnail(thumb, objectKey)
    } catch {
      emit('error', '缩略图上传失败，列表将使用原图')
    }
    const previewUrl = URL.createObjectURL(compressed)
    blobUrls.push(previewUrl)
    keyByUrl[previewUrl] = objectKey
    editor.value?.chain().focus().setImage({ src: previewUrl, alt: '游记图片' }).run()
    emit('image-uploaded', objectKey)
  } catch (error) {
    emit('error', (error as Error).message || '图片上传失败')
  } finally {
    uploading.value = false
  }
}

function videoCount(): number {
  return editor.value?.getHTML().match(/<video/g)?.length ?? 0
}

async function insertVideoFile(file: File): Promise<void> {
  const isMov = file.type === 'video/quicktime' || file.name.toLowerCase().endsWith('.mov')
  if (!isMov && !['video/mp4', 'video/webm'].includes(file.type)) {
    emit('error', '仅支持 mp4、webm 或 mov 视频')
    return
  }
  if (file.size > 100 * 1024 * 1024) {
    emit('error', '单个视频不能超过 100MB')
    return
  }
  if (videoCount() >= props.maxVideos) {
    emit('error', `每篇游记最多插入 ${props.maxVideos} 个视频`)
    return
  }
  if (isMov) {
    try {
      await ElMessageBox.confirm(
        'MOV 视频在部分安卓/Chrome 设备可能无法播放，建议先在相册中导出 MP4。仍要上传吗？',
        'MOV 兼容性提示',
        { type: 'warning', confirmButtonText: '仍然上传', cancelButtonText: '取消' }
      )
    } catch {
      return
    }
  }
  uploading.value = true
  try {
    const objectKey = await uploadVideo(file)
    const previewUrl = URL.createObjectURL(file)
    blobUrls.push(previewUrl)
    keyByUrl[previewUrl] = objectKey
    editor.value?.chain().focus().insertContent({ type: 'video', attrs: { src: previewUrl } }).run()
    emit('video-uploaded', objectKey)
  } catch (error) {
    emit('error', (error as Error).message || '视频上传失败')
  } finally {
    uploading.value = false
  }
}

function pickVideo(): void {
  videoInput.value?.click()
}

function onVideoFileChange(event: Event): void {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (file) {
    void insertVideoFile(file)
  }
  input.value = ''
}

async function startPolish(retry = false): Promise<void> {
  const instance = editor.value
  if (!instance || polishStreaming.value) {
    return
  }
  const selection = instance.state.selection
  let from = selection.from
  let to = selection.to
  let text = instance.state.doc.textBetween(from, to, '\n')
  if (retry && !text.trim() && polishRange) {
    from = polishRange.from
    to = polishRange.to
    text = instance.state.doc.textBetween(from, to, '\n')
  }
  if (!text.trim()) {
    emit('error', '请先选中文本')
    return
  }
  if (text.length > 5000) {
    emit('error', '选中文本不能超过 5000 字')
    return
  }
  polishRange = { from, to }
  polishPreview.value = ''
  polishReady.value = false
  polishError.value = ''
  polishStatus.value = '正在连接 AI…'
  polishStreaming.value = true
  instance.setEditable(false)
  polishController = new AbortController()
  polishRequestId = newRequestId()
  try {
    await polishStream(
      polishRequestId,
      text,
      polishStyle.value,
      'selected',
      {
        onMeta: (source) => {
          polishStatus.value = `流式输出中 · 扣费来源 ${source}`
        },
        onDelta: (delta) => {
          polishPreview.value += delta
        },
        onDone: (totalChars) => {
          polishReady.value = true
          polishStatus.value = `润色完成（${totalChars} 字），确认后采用`
        },
        onCancelled: () => {
          polishStatus.value = '已取消（不退回额度）'
          polishPreview.value = ''
          polishRange = null
        },
        onError: (message) => {
          polishReady.value = false
          polishError.value = message
          polishStatus.value = ''
          emit('error', message)
        }
      },
      polishController.signal
    )
  } catch (error) {
    if ((error as Error).name === 'AbortError') {
      polishStatus.value = '已取消（不退回额度）'
      polishPreview.value = ''
      polishRange = null
    } else {
      polishError.value = (error as Error).message || '润色失败'
      polishStatus.value = ''
      emit('error', polishError.value)
    }
  } finally {
    polishStreaming.value = false
    instance.setEditable(true)
    polishController = null
  }
}

function adoptPolish(): void {
  if (!polishReady.value || !polishPreview.value || polishError.value) {
    return
  }
  replaceSelection()
  polishReady.value = false
  polishStatus.value = '已采用润色结果，可使用撤销恢复原文'
}

function replaceSelection(): void {
  const instance = editor.value
  if (!instance || !polishRange) {
    return
  }
  const html = polishPreview.value
    .split(/\r?\n/)
    .map((line) => escapeHtml(line))
    .join('<br>')
  instance.chain().focus().insertContentAt(polishRange, html).run()
  polishRange = null
  polishPreview.value = ''
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

async function cancelPolishStream(): Promise<void> {
  if (!polishStreaming.value || !polishRequestId) {
    return
  }
  try {
    await cancelPolish(polishRequestId)
  } catch (error) {
    emit('error', (error as Error).message || '取消失败')
  }
  polishController?.abort()
  polishStreaming.value = false
  polishStatus.value = '已取消（不退回额度）'
}

function dismissPolishPanel(): void {
  polishStatus.value = ''
  polishError.value = ''
  polishPreview.value = ''
  polishReady.value = false
  polishRange = null
}

function pickImage(): void {
  fileInput.value?.click()
}

function onFileChange(event: Event): void {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (file) {
    void insertImageFile(file)
  }
  input.value = ''
}

function isActive(name: string, attrs?: Record<string, unknown>): boolean {
  return editor.value?.isActive(name, attrs) ?? false
}

function toggleBold(): void {
  editor.value?.chain().focus().toggleBold().run()
}

function toggleItalic(): void {
  editor.value?.chain().focus().toggleItalic().run()
}

function toggleStrike(): void {
  editor.value?.chain().focus().toggleStrike().run()
}

function toggleHeading(level: 2 | 3): void {
  editor.value?.chain().focus().toggleHeading({ level }).run()
}

function toggleBulletList(): void {
  editor.value?.chain().focus().toggleBulletList().run()
}

function toggleOrderedList(): void {
  editor.value?.chain().focus().toggleOrderedList().run()
}

function toggleBlockquote(): void {
  editor.value?.chain().focus().toggleBlockquote().run()
}

function setLink(): void {
  const previous = editor.value?.getAttributes('link').href ?? ''
  const url = window.prompt('链接地址（http/https）', previous)
  if (url === null) {
    return
  }
  const trimmed = url.trim()
  if (!trimmed) {
    editor.value?.chain().focus().extendMarkRange('link').unsetLink().run()
    return
  }
  if (!/^https?:\/\//i.test(trimmed)) {
    emit('error', '仅支持 http/https 链接')
    return
  }
  editor.value?.chain().focus().extendMarkRange('link').setLink({ href: trimmed }).run()
}

function undo(): void {
  editor.value?.chain().focus().undo().run()
}

function redo(): void {
  editor.value?.chain().focus().redo().run()
}
</script>

<template>
  <div class="rich-editor">
    <div class="rich-editor-toolbar">
      <el-tooltip content="加粗选中文本（Ctrl+B）" placement="top" :show-after="300">
        <el-button size="small" :type="isActive('bold') ? 'primary' : 'default'" @click="toggleBold">加粗</el-button>
      </el-tooltip>
      <el-tooltip content="斜体选中文本（Ctrl+I）" placement="top" :show-after="300">
        <el-button size="small" :type="isActive('italic') ? 'primary' : 'default'" @click="toggleItalic">斜体</el-button>
      </el-tooltip>
      <el-tooltip content="给选中文本加删除线" placement="top" :show-after="300">
        <el-button size="small" :type="isActive('strike') ? 'primary' : 'default'" @click="toggleStrike">删除线</el-button>
      </el-tooltip>
      <el-tooltip content="设为二级标题" placement="top" :show-after="300">
        <el-button size="small" :type="isActive('heading', { level: 2 }) ? 'primary' : 'default'" @click="toggleHeading(2)">标题</el-button>
      </el-tooltip>
      <el-tooltip content="设为三级小标题" placement="top" :show-after="300">
        <el-button size="small" :type="isActive('heading', { level: 3 }) ? 'primary' : 'default'" @click="toggleHeading(3)">小标题</el-button>
      </el-tooltip>
      <el-tooltip content="切换为无序列表" placement="top" :show-after="300">
        <el-button size="small" :type="isActive('bulletList') ? 'primary' : 'default'" @click="toggleBulletList">无序列表</el-button>
      </el-tooltip>
      <el-tooltip content="切换为有序列表" placement="top" :show-after="300">
        <el-button size="small" :type="isActive('orderedList') ? 'primary' : 'default'" @click="toggleOrderedList">有序列表</el-button>
      </el-tooltip>
      <el-tooltip content="将当前段落设为引用" placement="top" :show-after="300">
        <el-button size="small" :type="isActive('blockquote') ? 'primary' : 'default'" @click="toggleBlockquote">引用</el-button>
      </el-tooltip>
      <el-tooltip content="为选中文本添加链接" placement="top" :show-after="300">
        <el-button size="small" :type="isActive('link') ? 'primary' : 'default'" @click="setLink">链接</el-button>
      </el-tooltip>
      <el-tooltip content="选择 AI 润色的语言风格" placement="top" :show-after="300">
        <el-select v-model="polishStyle" size="small" style="width: 92px" :disabled="polishStreaming">
          <el-option label="生动" value="vivid" />
          <el-option label="简洁" value="concise" />
          <el-option label="优雅" value="elegant" />
        </el-select>
      </el-tooltip>
      <el-tooltip
        content="先选中 1–5000 字正文，再点击；结果会先预览，确认后采用"
        placement="top"
        :show-after="300"
      >
        <el-button
          size="small"
          :type="canPolish ? 'primary' : 'default'"
          :disabled="polishStreaming"
          @click="startPolish()"
        >
          AI 润色
        </el-button>
      </el-tooltip>
      <el-tooltip content="停止本次润色并保持原文" placement="top" :show-after="300">
        <el-button v-if="polishStreaming" size="small" type="danger" plain @click="cancelPolishStream">
          取消润色
        </el-button>
      </el-tooltip>
      <el-tooltip content="上传图片并插入到光标位置（单张 ≤5MB）" placement="top" :show-after="300">
        <el-button size="small" :loading="uploading" @click="pickImage">插入图片</el-button>
      </el-tooltip>
      <el-tooltip content="上传视频并插入到光标位置（mp4/webm/mov，单个 ≤100MB；MOV 在部分安卓/Chrome 设备可能无法播放）" placement="top" :show-after="300">
        <el-button size="small" :loading="uploading" @click="pickVideo">插入视频</el-button>
      </el-tooltip>
      <el-tooltip content="撤销上一步编辑，没有可撤销内容时不可用" placement="top" :show-after="300">
        <span class="tooltip-wrap">
          <el-button size="small" :disabled="!editor?.can().undo()" @click="undo">撤销</el-button>
        </span>
      </el-tooltip>
      <el-tooltip content="重做被撤销的编辑，没有可重做内容时不可用" placement="top" :show-after="300">
        <span class="tooltip-wrap">
          <el-button size="small" :disabled="!editor?.can().redo()" @click="redo">重做</el-button>
        </span>
      </el-tooltip>
      <input
        ref="fileInput"
        type="file"
        accept="image/jpeg,image/png,image/webp"
        style="display: none"
        @change="onFileChange"
      />
      <input
        ref="videoInput"
        type="file"
        accept="video/mp4,video/webm,video/quicktime,.mov"
        style="display: none"
        @change="onVideoFileChange"
      />
    </div>
    <div
      v-if="polishStreaming || polishStatus || polishError || polishPreview"
      class="polish-panel"
      :class="{ 'polish-panel-ready': polishReady }"
    >
      <div class="polish-head">
        <strong class="polish-heading">AI 润色结果</strong>
        <span class="meta">{{ polishError || polishStatus }}</span>
        <div class="polish-actions">
          <el-button v-if="polishReady" type="primary" size="small" @click="adoptPolish">
            采用
          </el-button>
          <el-button v-if="polishReady" size="small" @click="dismissPolishPanel">放弃</el-button>
          <el-button
            v-if="polishError && !polishStreaming"
            size="small"
            type="primary"
            plain
            @click="startPolish(true)"
          >
            重试
          </el-button>
          <el-button v-if="!polishStreaming && !polishReady" text size="small" @click="dismissPolishPanel">
            关闭
          </el-button>
        </div>
      </div>
      <pre v-if="polishPreview" class="polish-preview">{{ polishPreview }}</pre>
      <div v-if="polishReady" class="meta polish-hint">结果尚未写入正文，点击「采用」替换选中文本。</div>
    </div>
    <EditorContent :editor="editor" class="rich-editor-body" />
  </div>
</template>

<style scoped>
.rich-editor {
  margin-top: 12px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  overflow: hidden;
}

.rich-editor-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 8px;
  background: #f5f7fa;
  border-bottom: 1px solid #e4e7ed;
}

.rich-editor-toolbar .el-button + .el-button {
  margin-left: 0;
}

.rich-editor-body {
  min-height: 260px;
}

.rich-editor-body :deep(.rich-editor-content) {
  min-height: 260px;
  padding: 12px 16px;
  outline: none;
  line-height: 1.8;
}

.rich-editor-body :deep(.rich-editor-content img) {
  max-width: 100%;
  border-radius: 6px;
}

.rich-editor-body :deep(.rich-editor-content video) {
  display: block;
  max-width: 100%;
  max-height: 360px;
  margin: 8px 0;
  border-radius: 6px;
  background: #000;
}

.tooltip-wrap {
  display: inline-flex;
}

.polish-panel {
  padding: 10px 12px;
  background: #f5f9ff;
  border-bottom: 1px solid #e4e7ed;
  border-left: 3px solid #a0b4f5;
}

.polish-panel-ready {
  background: #eff6ff;
  border-left-color: var(--tm-brand, #3b5bdb);
}

.polish-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 12px;
}

.polish-heading {
  color: var(--tm-brand, #3b5bdb);
  font-size: 13px;
}

.polish-actions {
  display: flex;
  gap: 6px;
  margin-left: auto;
}

.polish-hint {
  margin-top: 6px;
}

.polish-preview {
  margin: 8px 0 0;
  padding: 8px 10px;
  max-height: 220px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.7;
  background: #ffffff;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
}

.rich-editor-body :deep(.rich-editor-content blockquote) {
  margin: 8px 0;
  padding-left: 12px;
  border-left: 3px solid #c0c4cc;
  color: #606266;
}

.rich-editor-body :deep(.rich-editor-content p.is-editor-empty:first-child::before) {
  content: attr(data-placeholder);
  float: left;
  height: 0;
  color: #a8abb2;
  pointer-events: none;
}
</style>
