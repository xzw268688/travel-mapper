<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCountries } from '../api/catalog'
import {
  createAdminCity,
  deleteAdminCity,
  listAdminCities,
  updateAdminCity,
  type AdminCityView
} from '../api/admin'
import type { Country } from '../api/types'

const countries = ref<Country[]>([])
const countryCode = ref('')
const status = ref('')
const keyword = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)
const cities = ref<AdminCityView[]>([])
const loading = ref(false)

const createDialog = reactive({
  visible: false,
  countryCode: 'CN',
  cityCode: '',
  name: '',
  level: 'CITY',
  saving: false
})
const editDialog = reactive({
  visible: false,
  id: 0,
  name: '',
  status: 'ENABLED',
  saving: false
})

onMounted(async () => {
  try {
    countries.value = await getCountries()
  } catch (error) {
    ElMessage.error((error as Error).message || '加载国家失败')
  }
  await load()
})

async function load(): Promise<void> {
  loading.value = true
  try {
    const result = await listAdminCities({
      countryCode: countryCode.value || undefined,
      status: status.value || undefined,
      keyword: keyword.value.trim() || undefined,
      page: page.value,
      size: size.value
    })
    cities.value = result.list
    total.value = result.total
  } catch (error) {
    ElMessage.error((error as Error).message || '加载城市失败')
  } finally {
    loading.value = false
  }
}

function search(): void {
  page.value = 1
  void load()
}

function openCreate(): void {
  createDialog.countryCode = countryCode.value || 'CN'
  createDialog.cityCode = ''
  createDialog.name = ''
  createDialog.level = 'CITY'
  createDialog.visible = true
}

async function submitCreate(): Promise<void> {
  if (!createDialog.countryCode || !createDialog.cityCode.trim() || !createDialog.name.trim()) {
    ElMessage.warning('请填写国家、城市代码和名称')
    return
  }
  createDialog.saving = true
  try {
    await createAdminCity({
      countryCode: createDialog.countryCode,
      cityCode: createDialog.cityCode.trim().toUpperCase(),
      name: createDialog.name.trim(),
      level: createDialog.level
    })
    createDialog.visible = false
    await load()
    ElMessage.success('城市已新增')
  } catch (error) {
    ElMessage.error((error as Error).message || '新增失败')
  } finally {
    createDialog.saving = false
  }
}

function openEdit(city: AdminCityView): void {
  editDialog.id = city.id
  editDialog.name = city.name
  editDialog.status = city.status
  editDialog.visible = true
}

async function submitEdit(): Promise<void> {
  if (!editDialog.name.trim()) {
    ElMessage.warning('名称不能为空')
    return
  }
  editDialog.saving = true
  try {
    await updateAdminCity(editDialog.id, {
      name: editDialog.name.trim(),
      status: editDialog.status
    })
    editDialog.visible = false
    await load()
    ElMessage.success('城市已更新')
  } catch (error) {
    ElMessage.error((error as Error).message || '更新失败')
  } finally {
    editDialog.saving = false
  }
}

async function remove(city: AdminCityView): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `删除城市 ${city.name}（${city.cityCode}）？被引用时无法删除，可改为停用。`,
      '删除城市',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await deleteAdminCity(city.id)
    await load()
    ElMessage.success('城市已删除')
  } catch (error) {
    ElMessage.error((error as Error).message || '删除失败')
  }
}
</script>

<template>
  <div class="plan-page">
    <el-card>
      <template #header><div class="trip-title">城市维护</div></template>
      <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
        <el-select v-model="countryCode" placeholder="全部国家" clearable filterable style="width: 180px">
          <el-option v-for="item in countries" :key="item.code" :label="item.nameZh" :value="item.code" />
        </el-select>
        <el-select v-model="status" placeholder="全部状态" clearable style="width: 140px">
          <el-option label="启用" value="ENABLED" />
          <el-option label="停用" value="DISABLED" />
        </el-select>
        <el-input v-model="keyword" placeholder="城市名称 / 代码" style="width: 220px" @keyup.enter="search" />
        <el-button type="primary" @click="search">查询</el-button>
        <el-button type="success" @click="openCreate">新增城市</el-button>
      </div>
    </el-card>

    <el-card style="margin-top: 16px" v-loading="loading">
      <el-table :data="cities" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="countryCode" label="国家" width="90" />
        <el-table-column prop="cityCode" label="城市代码" width="110" />
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column prop="level" label="层级" width="100" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="被引用" width="100">
          <template #default="{ row }">{{ row.referenced ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button text size="small" type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        style="margin-top: 12px; justify-content: flex-end"
        @current-change="load"
        @size-change="search"
      />
    </el-card>

    <el-dialog v-model="createDialog.visible" title="新增城市" width="460px">
      <el-form label-width="90px">
        <el-form-item label="国家" required>
          <el-select v-model="createDialog.countryCode" filterable style="width: 100%">
            <el-option v-for="item in countries" :key="item.code" :label="`${item.nameZh}（${item.code}）`" :value="item.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="城市代码" required>
          <el-input v-model="createDialog.cityCode" maxlength="8" placeholder="2-8 位大写字母/数字，如 LYA" />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="createDialog.name" maxlength="64" />
        </el-form-item>
        <el-form-item label="层级">
          <el-select v-model="createDialog.level" style="width: 100%">
            <el-option label="地级市" value="CITY" />
            <el-option label="主要城市" value="MAJOR" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="createDialog.saving" @click="submitCreate">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editDialog.visible" title="编辑城市" width="420px">
      <el-form label-width="80px">
        <el-form-item label="名称" required>
          <el-input v-model="editDialog.name" maxlength="64" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="editDialog.status" style="width: 100%">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="editDialog.saving" @click="submitEdit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>
