import { createBrowserRouter, Navigate } from 'react-router-dom'
import { CustomerLayout } from './routes/customer/Layout'
import { CustomerHome } from './routes/customer/Home'
import { CustomerProducts } from './routes/customer/Products'
import { CustomerProductDetail } from './routes/customer/ProductDetail'
import { CustomerCart } from './routes/customer/Cart'
import { CustomerOrders } from './routes/customer/Orders'
import { CustomerOrderDetail } from './routes/customer/OrderDetail'
import { CustomerBalance } from './routes/customer/Balance'
import { CustomerLogin } from './routes/customer/Login'
import { CustomerSignup } from './routes/customer/Signup'
import { CustomerSignupVerify } from './routes/customer/SignupVerify'
import { ProtectedRoute } from './shared/auth/ProtectedRoute'
import { SellerLayout } from './routes/seller/Layout'
import { SellerProducts } from './routes/seller/Products'
import { SellerOrders } from './routes/seller/Orders'

export const router = createBrowserRouter([
  { path: '/', element: <Navigate to="/customer" replace /> },
  {
    path: '/customer',
    element: <CustomerLayout />,
    children: [
      { index: true, element: <CustomerHome /> },
      { path: 'login', element: <CustomerLogin /> },
      { path: 'signup', element: <CustomerSignup /> },
      { path: 'signup/verify', element: <CustomerSignupVerify /> },
      {
        path: 'products',
        element: (
          <ProtectedRoute role="CUSTOMER">
            <CustomerProducts />
          </ProtectedRoute>
        ),
      },
      {
        path: 'products/:id',
        element: (
          <ProtectedRoute role="CUSTOMER">
            <CustomerProductDetail />
          </ProtectedRoute>
        ),
      },
      {
        path: 'cart',
        element: (
          <ProtectedRoute role="CUSTOMER">
            <CustomerCart />
          </ProtectedRoute>
        ),
      },
      {
        path: 'orders',
        element: (
          <ProtectedRoute role="CUSTOMER">
            <CustomerOrders />
          </ProtectedRoute>
        ),
      },
      {
        path: 'orders/:orderId',
        element: (
          <ProtectedRoute role="CUSTOMER">
            <CustomerOrderDetail />
          </ProtectedRoute>
        ),
      },
      {
        path: 'balance',
        element: (
          <ProtectedRoute role="CUSTOMER">
            <CustomerBalance />
          </ProtectedRoute>
        ),
      },
    ],
  },
  {
    path: '/seller',
    element: <SellerLayout />,
    children: [
      { index: true, element: <Navigate to="products" replace /> },
      { path: 'products', element: <SellerProducts /> },
      { path: 'orders', element: <SellerOrders /> },
    ],
  },
])
