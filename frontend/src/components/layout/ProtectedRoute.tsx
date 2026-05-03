import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuthStore } from '@/store/authStore'

export default function ProtectedRoute() {
  const { isAuthenticated } = useAuthStore()
  const location = useLocation()

  if (!isAuthenticated) {
    // Login sayfasına yönlendirirken gelinen yolu state'e kaydet.
    // LoginPage bu state'i okuyarak başarılı girişten sonra doğru sayfaya döner.
    return <Navigate to="/login" state={{ from: location.pathname }} replace />
  }

  return <Outlet />
}
