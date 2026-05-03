import { useState } from 'react'
import { Link, useNavigate, useLocation } from 'react-router-dom'
import { Mail, Lock, Package } from 'lucide-react'
import toast from 'react-hot-toast'
import { authApi } from '@/api/auth'
import { userApi } from '@/api/user'
import { useAuthStore } from '@/store/authStore'
import Button from '@/components/ui/Button'
import Input from '@/components/ui/Input'
import { getErrorMessage } from '@/lib/utils'
import { useQueryClient } from '@tanstack/react-query'

export default function LoginPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const location = useLocation()
  const { setToken, setAuth } = useAuthStore()
  const from = (location.state as { from?: string })?.from ?? '/'

  const [form, setForm] = useState({ email: '', password: '' })
  const [loading, setLoading] = useState(false)
  const [errors, setErrors] = useState<Record<string, string>>({})

  const validate = () => {
    const e: Record<string, string> = {}
    if (!form.email) e.email = 'E-posta gerekli'
    if (!form.password) e.password = 'Şifre gerekli'
    setErrors(e)
    return Object.keys(e).length === 0
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!validate()) return
    setLoading(true)
    queryClient.clear()
    try {
      const tokens = await authApi.login(form.email, form.password)
      // Token'ı önce store'a yaz ki getMe() interceptor'ı görebilsin
      setToken(tokens.access_token, tokens.refresh_token)
      const user = await userApi.getMe()
      setAuth(tokens.access_token, tokens.refresh_token, user)
      toast.success(`Hoş geldin, ${user.firstName}!`)
      navigate(from, { replace: true })
    } catch (err) {
      toast.error(getErrorMessage(err))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-dvh bg-gradient-to-br from-slate-50 via-indigo-50/30 to-violet-50/20 flex items-center justify-center px-4">
      <div className="w-full max-w-sm animate-fade-in-up">
        {/* Logo */}
        <div className="text-center mb-8">
          <div className="inline-flex w-14 h-14 bg-gradient-to-br from-indigo-500 to-violet-600 rounded-2xl items-center justify-center mb-4 shadow-lg shadow-indigo-200">
            <Package className="w-7 h-7 text-white" />
          </div>
          <h1 className="text-2xl font-bold text-slate-900">Tekrar hoş geldin</h1>
          <p className="text-sm text-slate-500 mt-1">Hesabına giriş yap</p>
        </div>

        {/* Card */}
        <div className="bg-white rounded-3xl shadow-xl shadow-slate-200/60 border border-slate-100 p-8">
          <form onSubmit={handleSubmit} className="space-y-4">
            <Input
              label="E-posta"
              type="email"
              placeholder="ornek@email.com"
              value={form.email}
              onChange={(e) => setForm({ ...form, email: e.target.value })}
              error={errors.email}
              icon={<Mail className="w-4 h-4" />}
              autoComplete="email"
            />
            <Input
              label="Şifre"
              type="password"
              placeholder="••••••••"
              value={form.password}
              onChange={(e) => setForm({ ...form, password: e.target.value })}
              error={errors.password}
              icon={<Lock className="w-4 h-4" />}
              autoComplete="current-password"
            />
            <Button type="submit" loading={loading} className="w-full mt-2" size="lg">
              Giriş Yap
            </Button>
          </form>
        </div>

        <p className="text-center text-sm text-slate-500 mt-6">
          Hesabın yok mu?{' '}
          <Link to="/register" className="font-semibold text-indigo-600 hover:text-indigo-700">
            Kayıt Ol
          </Link>
        </p>
      </div>
    </div>
  )
}
