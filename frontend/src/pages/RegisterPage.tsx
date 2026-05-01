import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Mail, Lock, User, Package } from 'lucide-react'
import toast from 'react-hot-toast'
import { userApi } from '@/api/user'
import { authApi } from '@/api/auth'
import { useAuthStore } from '@/store/authStore'
import Button from '@/components/ui/Button'
import Input from '@/components/ui/Input'
import { getErrorMessage } from '@/lib/utils'

export default function RegisterPage() {
  const navigate = useNavigate()
  const { setToken, setAuth } = useAuthStore()

  const [form, setForm] = useState({
    firstName: '',
    lastName: '',
    email: '',
    password: '',
    confirmPassword: '',
  })
  const [loading, setLoading] = useState(false)
  const [errors, setErrors] = useState<Record<string, string>>({})

  const validate = () => {
    const e: Record<string, string> = {}
    if (!form.firstName.trim()) e.firstName = 'Ad gerekli'
    if (!form.lastName.trim()) e.lastName = 'Soyad gerekli'
    if (!form.email) e.email = 'E-posta gerekli'
    if (!form.password) e.password = 'Şifre gerekli'
    else if (form.password.length < 8) e.password = 'En az 8 karakter'
    if (form.password !== form.confirmPassword) e.confirmPassword = 'Şifreler eşleşmiyor'
    setErrors(e)
    return Object.keys(e).length === 0
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!validate()) return
    setLoading(true)
    try {
      await userApi.signup({
        firstName: form.firstName,
        lastName: form.lastName,
        email: form.email,
        password: form.password,
      })
      // Auto-login after register
      const tokens = await authApi.login(form.email, form.password)
      // Token'ı önce store'a yaz ki getMe() interceptor'ı görebilsin
      setToken(tokens.access_token, tokens.refresh_token)
      const user = await userApi.getMe()
      setAuth(tokens.access_token, tokens.refresh_token, user)
      toast.success('Hesabın oluşturuldu!')
      navigate('/')
    } catch (err) {
      toast.error(getErrorMessage(err))
    } finally {
      setLoading(false)
    }
  }

  const f = (field: keyof typeof form) => ({
    value: form[field],
    onChange: (e: React.ChangeEvent<HTMLInputElement>) =>
      setForm({ ...form, [field]: e.target.value }),
    error: errors[field],
  })

  return (
    <div className="min-h-dvh bg-gradient-to-br from-slate-50 via-indigo-50/30 to-violet-50/20 flex items-center justify-center px-4 py-10">
      <div className="w-full max-w-sm animate-fade-in-up">
        {/* Logo */}
        <div className="text-center mb-8">
          <div className="inline-flex w-14 h-14 bg-gradient-to-br from-indigo-500 to-violet-600 rounded-2xl items-center justify-center mb-4 shadow-lg shadow-indigo-200">
            <Package className="w-7 h-7 text-white" />
          </div>
          <h1 className="text-2xl font-bold text-slate-900">Hesap oluştur</h1>
          <p className="text-sm text-slate-500 mt-1">Ücretsiz kaydol, alışverişe başla</p>
        </div>

        {/* Card */}
        <div className="bg-white rounded-3xl shadow-xl shadow-slate-200/60 border border-slate-100 p-8">
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid grid-cols-2 gap-3">
              <Input label="Ad" placeholder="Ali" {...f('firstName')} icon={<User className="w-4 h-4" />} />
              <Input label="Soyad" placeholder="Yılmaz" {...f('lastName')} />
            </div>
            <Input label="E-posta" type="email" placeholder="ornek@email.com" {...f('email')} icon={<Mail className="w-4 h-4" />} autoComplete="email" />
            <Input label="Şifre" type="password" placeholder="En az 8 karakter" {...f('password')} icon={<Lock className="w-4 h-4" />} />
            <Input label="Şifre Tekrar" type="password" placeholder="Şifreyi tekrar gir" {...f('confirmPassword')} icon={<Lock className="w-4 h-4" />} />
            <Button type="submit" loading={loading} className="w-full mt-2" size="lg">
              Kayıt Ol
            </Button>
          </form>
        </div>

        <p className="text-center text-sm text-slate-500 mt-6">
          Zaten hesabın var mı?{' '}
          <Link to="/login" className="font-semibold text-indigo-600 hover:text-indigo-700">
            Giriş Yap
          </Link>
        </p>
      </div>
    </div>
  )
}
