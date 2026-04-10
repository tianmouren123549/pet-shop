// 模拟商品数据
export const mockProducts = [
  {
    productId: 1,
    title: '皇家金毛幼犬粮 12kg 专用配方',
    categoryId: 4,
    categoryName: '幼犬粮',
    brandId: 1,
    brandName: '皇家',
    price: 458.00,
    stock: 120,
    status: 1,
    detail: {
      description: '皇家金毛幼犬粮专为2-15个月龄金毛幼犬设计，含有丰富的DHA和EPA，促进大脑发育和视力发育。特殊的颗粒形状适合金毛幼犬的嘴型，易于咀嚼。添加益生元组合，维护肠道健康。',
      specJson: {
        '适用年龄': '2-15个月',
        '净含量': '12kg',
        '主要成分': '鸡肉、大米、玉米',
        '蛋白质含量': '≥32%',
        '脂肪含量': '≥14%',
        '产地': '法国'
      }
    }
  },
  {
    productId: 2,
    title: '冠能金毛成犬粮 15kg 营养均衡',
    categoryId: 5,
    categoryName: '成犬粮',
    brandId: 2,
    brandName: '冠能',
    price: 528.00,
    stock: 85,
    status: 1,
    detail: {
      description: '冠能金毛成犬粮采用优质鸡肉为主要蛋白质来源，添加葡萄糖胺和软骨素，保护关节健康。Omega-3和Omega-6脂肪酸比例科学，维护皮肤和毛发健康。',
      specJson: {
        '适用年龄': '15个月以上',
        '净含量': '15kg',
        '主要成分': '鸡肉、大米、鱼油',
        '蛋白质含量': '≥26%',
        '脂肪含量': '≥16%',
        '产地': '美国'
      }
    }
  },
  {
    productId: 3,
    title: '比瑞吉金毛专用粮 10kg 天然粮',
    categoryId: 5,
    categoryName: '成犬粮',
    brandId: 3,
    brandName: '比瑞吉',
    price: 368.00,
    stock: 150,
    status: 1,
    detail: {
      description: '比瑞吉金毛专用粮采用天然食材，不含人工色素和防腐剂。添加深海鱼油，富含Omega-3，让毛发更加亮泽。特别添加软骨素，呵护金毛的关节健康。',
      specJson: {
        '适用年龄': '成犬',
        '净含量': '10kg',
        '主要成分': '鸡肉、糙米、深海鱼油',
        '蛋白质含量': '≥28%',
        '脂肪含量': '≥15%',
        '产地': '中国'
      }
    }
  },
  {
    productId: 4,
    title: '渴望六种鱼全犬粮 11.4kg 无谷配方',
    categoryId: 5,
    categoryName: '成犬粮',
    brandId: 4,
    brandName: '渴望',
    price: 798.00,
    stock: 45,
    status: 1,
    detail: {
      description: '渴望六种鱼全犬粮采用新鲜完整的鱼类制成，包含沙丁鱼、鲭鱼、鲱鱼等六种鱼类。无谷配方，适合所有犬种和生命阶段。85%动物性成分，15%蔬菜水果，符合犬类生物学饮食需求。',
      specJson: {
        '适用年龄': '全犬龄',
        '净含量': '11.4kg',
        '主要成分': '六种鱼类、豌豆、扁豆',
        '蛋白质含量': '≥38%',
        '脂肪含量': '≥18%',
        '产地': '加拿大'
      }
    }
  },
  {
    productId: 5,
    title: '爱肯拿鸭肉梨配方犬粮 11.4kg',
    categoryId: 5,
    categoryName: '成犬粮',
    brandId: 5,
    brandName: '爱肯拿',
    price: 688.00,
    stock: 60,
    status: 1,
    detail: {
      description: '爱肯拿鸭肉梨配方犬粮采用单一动物蛋白源，适合肠胃敏感的狗狗。新鲜鸭肉含量高达50%，添加巴特利梨，提供天然纤维。无谷配方，低升糖指数。',
      specJson: {
        '适用年龄': '全犬龄',
        '净含量': '11.4kg',
        '主要成分': '鸭肉、梨、豌豆',
        '蛋白质含量': '≥31%',
        '脂肪含量': '≥17%',
        '产地': '加拿大'
      }
    }
  },
  {
    productId: 6,
    title: '皇家幼猫粮 2kg K36',
    categoryId: 6,
    categoryName: '幼猫粮',
    brandId: 1,
    brandName: '皇家',
    price: 168.00,
    stock: 200,
    status: 1,
    detail: {
      description: '皇家幼猫粮K36专为4-12个月龄幼猫设计，高能量配方满足幼猫快速生长需求。添加益生元和高消化性蛋白，保护幼猫脆弱的消化系统。',
      specJson: {
        '适用年龄': '4-12个月',
        '净含量': '2kg',
        '主要成分': '鸡肉、大米',
        '蛋白质含量': '≥36%',
        '脂肪含量': '≥16%',
        '产地': '法国'
      }
    }
  },
  {
    productId: 7,
    title: '冠能成猫粮 7kg 室内猫配方',
    categoryId: 7,
    categoryName: '成猫粮',
    brandId: 2,
    brandName: '冠能',
    price: 298.00,
    stock: 130,
    status: 1,
    detail: {
      description: '冠能成猫粮室内猫配方，添加天然纤维，帮助排出毛球。优化的能量配方，防止室内猫肥胖。添加益生菌，促进肠道健康。',
      specJson: {
        '适用年龄': '1岁以上',
        '净含量': '7kg',
        '主要成分': '鸡肉、大米、甜菜浆',
        '蛋白质含量': '≥34%',
        '脂肪含量': '≥14%',
        '产地': '美国'
      }
    }
  },
  {
    productId: 8,
    title: '宠物互动玩具球 耐咬磨牙',
    categoryId: 8,
    categoryName: '玩具',
    brandId: null,
    brandName: null,
    price: 29.90,
    stock: 500,
    status: 1,
    detail: {
      description: '宠物互动玩具球采用天然橡胶材质，安全无毒。表面凹凸设计，可以清洁牙齿，按摩牙龈。内部可放置零食，增加互动乐趣。',
      specJson: {
        '材质': '天然橡胶',
        '尺寸': '直径7cm',
        '颜色': '蓝色/红色随机',
        '适用': '中大型犬',
        '产地': '中国'
      }
    }
  },
  {
    productId: 9,
    title: '宠物除臭消毒液 500ml',
    categoryId: 9,
    categoryName: '清洁用品',
    brandId: null,
    brandName: null,
    price: 39.90,
    stock: 300,
    status: 1,
    detail: {
      description: '宠物除臭消毒液采用植物提取配方，安全无刺激。有效去除宠物尿液、粪便等异味，同时具有消毒杀菌功能。适用于地板、笼子、猫砂盆等。',
      specJson: {
        '净含量': '500ml',
        '主要成分': '植物提取液、除菌因子',
        '香型': '清新柠檬',
        '适用范围': '宠物用品、家居环境',
        '产地': '中国'
      }
    }
  },
  {
    productId: 10,
    title: '比瑞吉小型犬成犬粮 5kg',
    categoryId: 5,
    categoryName: '成犬粮',
    brandId: 3,
    brandName: '比瑞吉',
    price: 198.00,
    stock: 180,
    status: 1,
    detail: {
      description: '比瑞吉小型犬成犬粮专为小型犬设计，颗粒小巧易咀嚼。添加L-肉碱，帮助维持理想体重。富含Omega-3和Omega-6，呵护皮肤和毛发。',
      specJson: {
        '适用年龄': '成犬',
        '净含量': '5kg',
        '主要成分': '鸡肉、大米',
        '蛋白质含量': '≥26%',
        '脂肪含量': '≥14%',
        '产地': '中国'
      }
    }
  }
]

// 模拟评论数据
export const mockReviews = {
  1: [
    {
      reviewId: 1,
      userId: 1,
      productId: 1,
      rating: 5,
      content: '我家金毛宝宝特别喜欢吃这款狗粮，吃了一个月毛色变得更亮了，便便也很正常，会继续回购！',
      goldenRetrieverScore: 0.9500,
      status: 1,
      createdAt: '2024-01-15T10:30:00'
    },
    {
      reviewId: 2,
      userId: 2,
      productId: 1,
      rating: 5,
      content: '金毛幼犬专用粮确实不错，颗粒大小合适，我家两个月的金毛吃得很香，消化也好。',
      goldenRetrieverScore: 0.9200,
      status: 1,
      createdAt: '2024-01-16T14:20:00'
    },
    {
      reviewId: 12,
      userId: 3,
      productId: 1,
      rating: 4,
      content: '金毛幼犬粮不错，就是颗粒有点大，我家两个月的小金毛刚开始吃有点费劲。',
      goldenRetrieverScore: 0.9000,
      status: 1,
      createdAt: '2024-01-20T09:15:00'
    }
  ],
  2: [
    {
      reviewId: 3,
      userId: 3,
      productId: 2,
      rating: 4,
      content: '狗粮质量不错，我家金毛成犬吃了半年了，体型保持得很好，就是价格有点贵。',
      goldenRetrieverScore: 0.8800,
      status: 1,
      createdAt: '2024-01-17T16:45:00'
    },
    {
      reviewId: 4,
      userId: 1,
      productId: 2,
      rating: 5,
      content: '冠能这个牌子一直很信赖，我家金毛从小吃到大，现在三岁了身体很健康，毛发也很漂亮。',
      goldenRetrieverScore: 0.9100,
      status: 1,
      createdAt: '2024-01-18T11:30:00'
    }
  ],
  3: [
    {
      reviewId: 5,
      userId: 2,
      productId: 3,
      rating: 5,
      content: '比瑞吉的狗粮性价比很高，我家金毛很爱吃，而且是天然粮，吃着放心。',
      goldenRetrieverScore: 0.8900,
      status: 1,
      createdAt: '2024-01-19T13:20:00'
    },
    {
      reviewId: 11,
      userId: 2,
      productId: 3,
      rating: 5,
      content: '这款狗粮我家金毛吃了快一年了，一直很稳定，毛色漂亮，体格健壮。',
      goldenRetrieverScore: 0.9300,
      status: 1,
      createdAt: '2024-01-25T15:40:00'
    }
  ],
  4: [
    {
      reviewId: 6,
      userId: 3,
      productId: 4,
      rating: 5,
      content: '渴望的狗粮真的很好，虽然贵但是值得，我家狗狗吃了之后毛发特别亮，体质也变好了。',
      goldenRetrieverScore: 0.3000,
      status: 1,
      createdAt: '2024-01-21T10:10:00'
    }
  ],
  5: [
    {
      reviewId: 7,
      userId: 1,
      productId: 5,
      rating: 4,
      content: '爱肯拿的鸭肉配方很适合我家肠胃敏感的狗狗，吃了之后拉肚子的情况明显改善了。',
      goldenRetrieverScore: 0.2500,
      status: 1,
      createdAt: '2024-01-22T14:55:00'
    }
  ],
  6: [
    {
      reviewId: 8,
      userId: 2,
      productId: 6,
      rating: 5,
      content: '皇家幼猫粮很不错，我家小猫咪吃得很香，长得也很快。',
      goldenRetrieverScore: 0.0000,
      status: 1,
      createdAt: '2024-01-23T09:30:00'
    }
  ],
  7: [
    {
      reviewId: 9,
      userId: 3,
      productId: 7,
      rating: 4,
      content: '室内猫粮配方不错，我家猫吃了之后毛球吐得少了，体重也控制得很好。',
      goldenRetrieverScore: 0.0000,
      status: 1,
      createdAt: '2024-01-24T16:20:00'
    }
  ],
  8: [
    {
      reviewId: 10,
      userId: 1,
      productId: 8,
      rating: 5,
      content: '玩具球质量很好，我家金毛特别喜欢玩，咬了一个月还完好无损。',
      goldenRetrieverScore: 0.7500,
      status: 1,
      createdAt: '2024-01-26T11:45:00'
    }
  ],
  9: [],
  10: []
}

// 模拟分类数据
export const mockCategories = [
  { categoryId: 1, parentId: 0, name: '狗粮', path: '1' },
  { categoryId: 2, parentId: 0, name: '猫粮', path: '2' },
  { categoryId: 3, parentId: 0, name: '宠物用品', path: '3' },
  { categoryId: 4, parentId: 1, name: '幼犬粮', path: '1/4' },
  { categoryId: 5, parentId: 1, name: '成犬粮', path: '1/5' },
  { categoryId: 6, parentId: 2, name: '幼猫粮', path: '2/6' },
  { categoryId: 7, parentId: 2, name: '成猫粮', path: '2/7' },
  { categoryId: 8, parentId: 3, name: '玩具', path: '3/8' },
  { categoryId: 9, parentId: 3, name: '清洁用品', path: '3/9' }
]

// 模拟购物车数据
export const mockCart = [
  { cartId: 1, userId: 1, productId: 1, quantity: 2 },
  { cartId: 2, userId: 1, productId: 8, quantity: 1 }
]
