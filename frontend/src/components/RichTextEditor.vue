<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { EditorContent, useEditor } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import Image from '@tiptap/extension-image'
import Link from '@tiptap/extension-link'
import Placeholder from '@tiptap/extension-placeholder'
import { uploadImage } from '../api/notes'
import { compressImage } from '../utils/image'
import { contentToPreview, previewToContent } from '../utils/noteContent'

const props = withDefaults(
  defineProps<{
    modelValue: string
    imageUrls?: Record<string, string>
    maxImages?: number
    placeholder?: string
  }>(),
  {
    imageUrls: () => ({}),
    maxImages: 20,
    placeholder: '记录旅途中的见闻…'
  }
)

const emit = defineEmits<{
  (event: 'update:modelValue', value: string): void
  (event: 'image-uploaded', objectKey: string): void
  (event: 'error', message: string): void
}>()

const fileInput = ref<HTMLInputElement | null>(null)
const uploading = ref(false)
const keyByUrl: Record<string, string> = {}
const blobUrls: string[] = []

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
  }
})

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
      <el-button size="small" :loading="uploading" @click="pickImage">插入图片</el-button>
      <el-button size="small" :disabled="!editor?.can().undo()" @click="undo">撤销</el-button>
      <el-button size="small" :disabled="!editor?.can().redo()" @click="redo">重做</el-button>
      <input
        ref="fileInput"
        type="file"
        accept="image/jpeg,image/png,image/webp"
        style="display: none"
        @change="onFileChange"
      />
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
