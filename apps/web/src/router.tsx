import { createBrowserRouter, Navigate } from 'react-router-dom'
import { CustomerLayout } from './routes/customer/Layout'
import { CustomerHome } from './routes/customer/Home'
import { CustomerProducts } from './routes/customer/Products'
import { CustomerCart } from './routes/customer/Cart'
import { CustomerOrders } from './routes/customer/Orders'
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
      { path: 'products', element: <CustomerProducts /> },
      { path: 'cart', element: <CustomerCart /> },
      { path: 'orders', element: <CustomerOrders /> },
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
