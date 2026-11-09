<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCountries } from '../api/catalog'
import {
  createOfficialLink,
  deleteOfficialLink,
  listOfficialLinks,
  updateOfficialLink,
  type AdminLinkView
} from '../api/admin'
import AdminPageIntro from '../components/AdminPageIntro.vue'
import type { Country } from '../api/types'

const countries = ref<Country[]>([])
const countryCode = ref('JP')
const links = ref<AdminLinkView[]>([])
const loading = ref(false)
const dialog = reactive({
  visible: false,
  id: 0,
  title: '',
  url: '',
  sortOrder: 0,
  saving: false
})

onMounted(async () => {
  try {
    countries.value = await getCountries()
    if (countries.value.length > 0 && !countries.value.some((item) => item.code === countryCode.value)) {
      countryCode.value = countries.value[0].code
    }
    await load()
  } catch (error) {
    ElMessage.error((error as Error).message || '加载失败')
  }
})

async function load(): Promise<void> {
  if (!countryCode.value) return
  loading.value = true
  try {
    links.value = await listOfficialLinks(countryCode.value)
  } catch (error) {
    ElMessage.error((error as Error).message || '加载官方链接失败')
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  dialog.id = 0
  dialog.title = ''
  dialog.url = ''
  dialog.sortOrder = links.value.length + 1
  dialog.visible = true
}

function openEdit(link: AdminLinkView): void {
  dialog.id = link.id
  dialog.title = link.title
  dialog.url = link.url
  dialog.sortOrder = link.sortOrder ?? 0
  dialog.visible = true
}

async function submit(): Promise<void> {
  if (!dialog.title.trim() || !dialog.url.trim()) {
    ElMessage.warning('请填写标题和 URL')
    return
  }
  dialog.saving = true
  try {
    const body = {
      countryCode: countryCode.value,
      title: dialog.title.trim(),
      url: dialog.url.trim(),
      sortOrder: dialog.sortOrder
    }
    if (dialog.id) {
      await updateOfficialLink(dialog.id, body)
    } else {
      await createOfficialLink(body)
    }
    dialog.visible = false
    await load()
    ElMessage.success('官方链接已保存')
  } catch (error) {
    ElMessage.error((error as Error).message || '保存失败')
  } finally {
    dialog.saving = false
  }
}

async function remove(link: AdminLinkView): Promise<void> {
  try {
    await ElMessageBox.confirm(`删除链接“${link.title}”？`, '删除链接', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await deleteOfficialLink(link.id)
    await load()
    ElMessage.success('链接已删除')
  } catch (error) {
    ElMessage.error((error as Error).message || '删除失败')
  }
}
</script>

<template>
  <div class="plan-page">
    <el-card>
      <template #header><div class="trip-title">官方链接维护</div></template>
      <AdminPageIntro title="这个页面是干什么的？">
        按国家维护最多 3 条官方链接（如签证、入境、旅游局），行程的行前准备页会展示这些链接；未配置时提供搜索入口作为兜底。
      </AdminPageIntro>
      <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
        <el-select v-model="countryCode" filterable style="width: 200px" @change="load">
          <el-option v-for="item in countries" :key="item.code" :label="item.nameZh" :value="item.code" />
        </el-select>
        <el-button @click="load">刷新</el-button>
        <el-button type="success" :disabled="links.length >= 3" @click="openCreate">新增链接</el-button>
        <span class="meta">每个国家最多 3 条；未配置时行程页会显示“去搜索”兜底。</span>
      </div>
    </el-card>

    <el-card style="margin-top: 16px" v-loading="loading">
      <el-empty v-if="links.length === 0" description="该国家暂无官方链接" />
      <el-table v-else :data="links" stripe>
        <el-table-column prop="sortOrder" label="排序" width="80" />
        <el-table-column prop="title" label="标题" min-width="180" />
        <el-table-column prop="url" label="URL" min-width="300" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button text size="small" type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialog.visible" :title="dialog.id ? '编辑官方链接' : '新增官方链接'" width="480px">
      <el-form label-width="90px">
        <el-form-item label="标题" required>
          <el-input v-model="dialog.title" maxlength="64" />
        </el-form-item>
        <el-form-item label="URL" required>
          <el-input v-model="dialog.url" maxlength="500" placeholder="https://…" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="dialog.sortOrder" :min="0" :max="99" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="submit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>
