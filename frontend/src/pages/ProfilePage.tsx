import { useState, useEffect } from 'react'
import { useQuery, useMutation } from '@tanstack/react-query'
import { User, Mail, Save } from 'lucide-react'
import toast from 'react-hot-toast'
import { userApi } from '@/api/user'
import { useAuthStore } from '@/store/authStore'
import { formatDate, getErrorMessage } from '@/lib/utils'
import Button from '@/components/ui/Button'
import Input from '@/components/ui/Input'
import { Skeleton } from '@/components/ui/Skeleton'

export default function ProfilePage() {
  const { setUser } = useAuthStore()
  const [editing, setEditing] = useState(false)
  const [form, setForm] = useState({ firstName: '', lastName: '', email: '' })

  const { data: profile, isLoading } = useQuery({
    queryKey: ['me'],
    queryFn: userApi.getMe,
  })

  useEffect(() => {
    if (profile) setForm({ firstName: profile.firstName, lastName: profile.lastName, email: profile.email })
  }, [profile])

  const mutation = useMutation({
    mutationFn: () => userApi.updateMe(form),
    onSuccess: (data) => {
      setUser(data)
      setEditing(false)
      toast.success('Profil güncellendi')
    },
    onError: (err) => toast.error(getErrorMessage(err)),
  })

  if (isLoading) {
    return (
      <div className="max-w-xl mx-auto px-4 py-8 space-y-4">
        <Skeleton className="h-24 rounded-2xl" />
        <Skeleton className="h-48 rounded-2xl" />
      </div>
    )
  }

  return (
    <div className="max-w-xl mx-auto px-4 sm:px-6 py-8">
      <h1 className="text-2xl font-bold text-slate-900 mb-8">Profilim</h1>

      {/* Avatar card */}
      <div className="bg-gradient-to-r from-indigo-600 to-violet-600 rounded-2xl p-6 mb-4 text-white flex items-center gap-4">
        <div className="w-16 h-16 bg-white/20 rounded-2xl flex items-center justify-center text-3xl font-bold">
          {profile?.firstName?.[0]?.toUpperCase()}
        </div>
        <div>
          <h2 className="text-lg font-bold">{profile?.firstName} {profile?.lastName}</h2>
          <p className="text-indigo-200 text-sm">{profile?.email}</p>
          <p className="text-indigo-300 text-xs mt-0.5">Üye: {profile?.createdAt ? formatDate(profile.createdAt) : '-'}</p>
        </div>
      </div>

      {/* Edit form */}
      <div className="bg-white rounded-2xl border border-slate-100 p-6">
        <div className="flex items-center justify-between mb-5">
          <h2 className="text-sm font-bold text-slate-800">Kişisel Bilgiler</h2>
          {!editing && (
            <Button variant="ghost" size="sm" onClick={() => setEditing(true)}>
              Düzenle
            </Button>
          )}
        </div>

        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <Input
              label="Ad"
              value={form.firstName}
              onChange={(e) => setForm({ ...form, firstName: e.target.value })}
              disabled={!editing}
              icon={<User className="w-4 h-4" />}
            />
            <Input
              label="Soyad"
              value={form.lastName}
              onChange={(e) => setForm({ ...form, lastName: e.target.value })}
              disabled={!editing}
            />
          </div>
          <Input
            label="E-posta"
            type="email"
            value={form.email}
            onChange={(e) => setForm({ ...form, email: e.target.value })}
            disabled={!editing}
            icon={<Mail className="w-4 h-4" />}
          />
        </div>

        {editing && (
          <div className="flex gap-3 mt-5">
            <Button
              onClick={() => mutation.mutate()}
              loading={mutation.isPending}
              icon={<Save className="w-4 h-4" />}
              className="flex-1"
            >
              Kaydet
            </Button>
            <Button
              variant="outline"
              onClick={() => {
                setEditing(false)
                setForm({ firstName: profile!.firstName, lastName: profile!.lastName, email: profile!.email })
              }}
              className="flex-1"
            >
              İptal
            </Button>
          </div>
        )}
      </div>
    </div>
  )
}
