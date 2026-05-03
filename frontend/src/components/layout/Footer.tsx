import { Package, Heart } from 'lucide-react'
import { Link } from 'react-router-dom'

export default function Footer() {
  return (
    <footer className="mt-auto border-t border-slate-100 bg-white">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-10">
        <div className="flex flex-col md:flex-row items-center justify-between gap-6">
          {/* Brand */}
          <div className="flex items-center gap-2.5">
            <div className="w-7 h-7 bg-gradient-to-br from-indigo-500 to-violet-600 rounded-lg flex items-center justify-center">
              <Package className="w-3.5 h-3.5 text-white" />
            </div>
            <span className="font-bold text-slate-800">
              n11<span className="text-indigo-600">shop</span>
            </span>
          </div>

          {/* Links */}
          <div className="flex items-center gap-6 text-sm text-slate-500">
            <Link to="/" className="hover:text-indigo-600 transition-colors">Ürünler</Link>
            <Link to="/cart" className="hover:text-indigo-600 transition-colors">Sepet</Link>
            <Link to="/orders" className="hover:text-indigo-600 transition-colors">Siparişler</Link>
          </div>

          {/* Credit */}
          <p className="flex items-center gap-1.5 text-sm text-slate-400">
            Made with <Heart className="w-3.5 h-3.5 text-red-400 fill-red-400" /> for n11 Bootcamp
          </p>
        </div>
      </div>
    </footer>
  )
}
