// The real sample schemas from the backend resources, inlined by Vite at build time.
// Importing them keeps one source of truth: the UI posts exactly what the backend ships.
import orderV1 from '../../../src/main/resources/samples/ecommerce-order/order-v1.avsc?raw';
import orderV2 from '../../../src/main/resources/samples/ecommerce-order/order-v2.avsc?raw';

export const ecommerceOrderSample = {
  projectName: 'E-commerce Orders',
  versions: [orderV1, orderV2],
};
