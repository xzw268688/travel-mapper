<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { EditorContent, useEditor } from '@tiptap/vue-3'
import { mergeAttributes, Node, type Editor as CoreEditor } from '@tiptap/core'
import StarterKit from '@tiptap/starter-kit'
import Image from '@tiptap/extension-image'
import Link from '@tiptap/extension-link'
import Placeholder from '@tiptap/extension-placeholder'
import { cancelPolish, polishStream } from '../api/ai'
import { uploadImage, uploadVideo } from '../api/notes'
import { compressImage } from '../utils/image'
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
  if (!['video/mp4', 'video/webm'].includes(file.type)) {
    emit('error', '仅支持 mp4 或 webm 视频')
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
          replaceSelection()
          polishStatus.value = `润色完成，已替换选中文本（${totalChars} 字），可使用撤销恢复`
        },
        onCancelled: () => {
          polishStatus.value = '已取消（不退回额度）'
        },
        onError: (message) => {
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
      <el-button size="small" :type="isActive('bold') ? 'primary' : 'default'" @click="toggleBold">加粗</el-button>
      <el-button size="small" :type="isActive('italic') ? 'primary' : 'default'" @click="toggleItalic">斜体</el-button>
      <el-button size="small" :type="isActive('strike') ? 'primary' : 'default'" @click="toggleStrike">删除线</el-button>
      <el-button size="small" :type="isActive('heading', { level: 2 }) ? 'primary' : 'default'" @click="toggleHeading(2)">标题</el-button>
      <el-button size="small" :type="isActive('heading', { level: 3 }) ? 'primary' : 'default'" @click="toggleHeading(3)">小标题</el-button>
      <el-button size="small" :type="isActive('bulletList') ? 'primary' : 'default'" @click="toggleBulletList">无序列表</el-button>
      <el-button size="small" :type="isActive('orderedList') ? 'primary' : 'default'" @click="toggleOrderedList">有序列表</el-button>
      <el-button size="small" :type="isActive('blockquote') ? 'primary' : 'default'" @click="toggleBlockquote">引用</el-button>
      <el-button size="small" :type="isActive('link') ? 'primary' : 'default'" @click="setLink">链接</el-button>
      <el-select v-model="polishStyle" size="small" style="width: 92px" :disabled="polishStreaming">
        <el-option label="生动" value="vivid" />
        <el-option label="简洁" value="concise" />
        <el-option label="优雅" value="elegant" />
      </el-select>
      <el-button
        size="small"
        :type="canPolish ? 'primary' : 'default'"
        :disabled="polishStreaming"
        @click="startPolish()"
      >
        AI 润色
      </el-button>
      <el-button v-if="polishStreaming" size="small" type="danger" plain @click="cancelPolishStream">
        取消润色
      </el-button>
      <el-button size="small" :loading="uploading" @click="pickImage">插入图片</el-button>
      <el-button size="small" :loading="uploading" @click="pickVideo">插入视频</el-button>
      <el-button size="small" :disabled="!editor?.can().undo()" @click="undo">撤销</el-button>
      <el-button size="small" :disabled="!editor?.can().redo()" @click="redo">重做</el-button>
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
        accept="video/mp4,video/webm"
        style="display: none"
        @change="onVideoFileChange"
      />
    </div>
    <div v-if="polishStreaming || polishStatus || polishError || polishPreview" class="polish-panel">
      <div class="polish-head">
        <span class="meta">{{ polishError || polishStatus }}</span>
        <div style="display: flex; gap: 6px">
          <el-button
            v-if="polishError && !polishStreaming"
            size="small"
            type="primary"
            plain
            @click="startPolish(true)"
          >
            重试
          </el-button>
          <el-button v-if="!polishStreaming" text size="small" @click="dismissPolishPanel">关闭</el-button>
        </div>
      </div>
      <pre v-if="polishPreview" class="polish-preview">{{ polishPreview }}</pre>
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

.polish-panel {
  padding: 10px 12px;
  background: #f5f9ff;
  border-bottom: 1px solid #e4e7ed;
}

.polish-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
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
