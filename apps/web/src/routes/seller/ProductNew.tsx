import { useFieldArray, useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useMutation } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { addProduct } from '@/shared/api/seller'
import { extractApiMessage } from '@/shared/api/client'
import { useAuth } from '@/shared/auth/AuthContext'
import { rememberMyProductId } from './productStore'

const itemSchema = z.object({
  name: z.string().min(1, '옵션명을 입력하세요'),
  // register('...', { valueAsNumber: true }) 로 string 이 number 로 변환된 후 검증.
  price: z.number({ message: '숫자' }).int('정수').min(0, '0 이상'),
  count: z.number({ message: '숫자' }).int('정수').min(0, '0 이상'),
})

const schema = z.object({
  name: z.string().min(1, '상품명을 입력하세요'),
  description: z.string().min(1, '설명을 입력하세요'),
  addProductItemForms: z.array(itemSchema).min(1, '옵션을 1개 이상 등록하세요'),
})

type FormData = z.infer<typeof schema>

export function SellerProductNew() {
  const { user } = useAuth()
  const navigate = useNavigate()

  const {
    register,
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: '',
      description: '',
      addProductItemForms: [{ name: '', price: 0, count: 0 }],
    },
  })

  const { fields, append, remove } = useFieldArray({
    control,
    name: 'addProductItemForms',
  })

  const createMutation = useMutation({
    mutationFn: addProduct,
    onSuccess: (res) => {
      rememberMyProductId(user?.id, res.productId)
      navigate('/seller/products')
    },
  })

  return (
    <section>
      <h1>신규 상품 등록</h1>
      <form
        onSubmit={handleSubmit((data) => createMutation.mutate(data))}
        className="form"
        style={{ maxWidth: 640 }}
      >
        <label>
          상품명
          <input {...register('name')} />
          {errors.name && <small className="err">{errors.name.message}</small>}
        </label>
        <label>
          설명
          <textarea rows={3} {...register('description')} />
          {errors.description && (
            <small className="err">{errors.description.message}</small>
          )}
        </label>

        <h2>옵션</h2>
        {errors.addProductItemForms?.root && (
          <p className="err">{errors.addProductItemForms.root.message}</p>
        )}
        <table className="table">
          <thead>
            <tr>
              <th>옵션명</th>
              <th>가격</th>
              <th>재고</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {fields.map((f, idx) => (
              <tr key={f.id}>
                <td>
                  <input {...register(`addProductItemForms.${idx}.name`)} />
                  {errors.addProductItemForms?.[idx]?.name && (
                    <small className="err">
                      {errors.addProductItemForms[idx]?.name?.message}
                    </small>
                  )}
                </td>
                <td>
                  <input
                    type="number"
                    {...register(`addProductItemForms.${idx}.price`, { valueAsNumber: true })}
                    style={{ width: 100 }}
                  />
                </td>
                <td>
                  <input
                    type="number"
                    {...register(`addProductItemForms.${idx}.count`, { valueAsNumber: true })}
                    style={{ width: 80 }}
                  />
                </td>
                <td>
                  <button
                    type="button"
                    onClick={() => remove(idx)}
                    disabled={fields.length === 1}
                  >
                    제거
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <p>
          <button
            type="button"
            onClick={() => append({ name: '', price: 0, count: 0 })}
          >
            ＋ 옵션 추가
          </button>
        </p>

        <button type="submit" disabled={createMutation.isPending}>
          {createMutation.isPending ? '등록 중...' : '상품 등록'}
        </button>
        {createMutation.isError && (
          <p className="err">{extractApiMessage(createMutation.error)}</p>
        )}
      </form>
    </section>
  )
}
