// 离线开发用示例商品（仅 USE_MOCK 时使用）
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
  },
  {
    productId: 11,
    title: '纽顿T28鲑鱼鳟鱼配方小型犬粮 6kg 加拿大进口',
    categoryId: 10,
    categoryName: '进口狗粮',
    brandId: 6,
    brandName: '纽顿',
    price: 525.0,
    stock: 80,
    status: 1,
    detail: {
      description: '加拿大原装进口纽顿无谷低升糖系列，鲑鱼与鳟鱼配方，适合小型犬与玩赏犬。',
      specJson: { 产地: '加拿大', 净含量: '6kg', 配方: '鲑鱼&鳟鱼', 适用: '小型犬' }
    }
  },
  {
    productId: 12,
    title: 'Instinct百利生鲜系列无谷鸡肉全犬粮 9.5kg 美国进口',
    categoryId: 10,
    categoryName: '进口狗粮',
    brandId: 7,
    brandName: 'Instinct百利',
    price: 1088.0,
    stock: 35,
    status: 1,
    detail: {
      description: '生鲜本能百利无谷鸡肉配方，高肉蛋白，添加冻干涂层，适口性佳。',
      specJson: { 产地: '美国', 净含量: '9.5kg', 适用: '全犬' }
    }
  },
  {
    productId: 13,
    title: '卡比四种肉配方全犬粮 44磅 美产',
    categoryId: 10,
    categoryName: '进口狗粮',
    brandId: 15,
    brandName: '卡比',
    price: 1029.0,
    stock: 42,
    status: 1,
    detail: {
      description: '卡比四种肉配方，含鸡肉火鸡羊肉与鱼，人类可食级原料标准。',
      specJson: { 产地: '美国', 规格: '44磅', 适用: '全犬' }
    }
  },
  {
    productId: 14,
    title: '伯纳天纯羊肉燕麦蔓越莓中大型成犬粮 15kg',
    categoryId: 11,
    categoryName: '国产狗粮',
    brandId: 13,
    brandName: '伯纳天纯',
    price: 466.0,
    stock: 95,
    status: 1,
    detail: {
      description: '伯纳天纯添加羊肉燕麦蔓越莓，健胃促吸收，蓬松亮毛，健骨护关节。',
      specJson: { 净含量: '15kg', 适用: '中大型成犬', 产地: '中国' }
    }
  },
  {
    productId: 15,
    title: '麦富迪牛肉双拼通用型成犬粮 10kg',
    categoryId: 11,
    categoryName: '国产狗粮',
    brandId: 8,
    brandName: '麦富迪',
    price: 335.0,
    stock: 220,
    status: 1,
    detail: {
      description: '麦富迪牛肉双拼粮，真牛肉粒添加，低温烘烤，通用型成犬。',
      specJson: { 净含量: '10kg', 牛肉粒: '20%', 产地: '中国' }
    }
  },
  {
    productId: 16,
    title: '醇粹金标无麸系列大型成犬粮 15kg',
    categoryId: 11,
    categoryName: '国产狗粮',
    brandId: 12,
    brandName: '醇粹',
    price: 489.0,
    stock: 60,
    status: 1,
    detail: {
      description: '醇粹金标无麸大型成犬粮，适合18月龄以上大型犬，均衡营养呵护关节。',
      specJson: { 净含量: '15kg', 适用: '大型成犬', 产地: '中国' }
    }
  },
  {
    productId: 17,
    title: '比瑞吉冻干生骨肉全价犬粮 鸭肉梨味 1.5kg',
    categoryId: 12,
    categoryName: '冻干狗粮',
    brandId: 3,
    brandName: '比瑞吉',
    price: 138.0,
    stock: 150,
    status: 1,
    detail: {
      description: '比瑞吉冻干生骨肉全价犬粮，鸭肉梨味，添加冻干颗粒，呵护肠道。',
      specJson: { 净含量: '1.5kg', 口味: '鸭肉梨', 产地: '中国' }
    }
  },
  {
    productId: 18,
    title: '纽顿鸡肉配方进口成猫粮 5.4kg',
    categoryId: 13,
    categoryName: '进口猫粮',
    brandId: 6,
    brandName: '纽顿',
    price: 398.0,
    stock: 70,
    status: 1,
    detail: {
      description: '纽顿鸡肉配方进口猫粮，低敏易消化，适合成猫日常饲喂。',
      specJson: { 净含量: '5.4kg', 产地: '加拿大', 适用: '成猫' }
    }
  },
  {
    productId: 19,
    title: '渴望鸡肉配方全猫粮 5.4kg 加拿大进口',
    categoryId: 13,
    categoryName: '进口猫粮',
    brandId: 4,
    brandName: '渴望',
    price: 588.0,
    stock: 55,
    status: 1,
    detail: {
      description: '渴望鸡肉配方全猫粮，高鲜肉含量，满足猫咪肉食天性。',
      specJson: { 净含量: '5.4kg', 产地: '加拿大', 适用: '成猫' }
    }
  },
  {
    productId: 20,
    title: '卫仕膳食平衡全价成猫粮 10kg',
    categoryId: 14,
    categoryName: '国产猫粮',
    brandId: 9,
    brandName: '卫仕',
    price: 268.0,
    stock: 180,
    status: 1,
    detail: {
      description: '卫仕膳食平衡全价成猫粮，营养配比均衡，适合室内饲养猫。',
      specJson: { 净含量: '10kg', 产地: '中国', 适用: '成猫' }
    }
  },
  {
    productId: 21,
    title: '麦富迪冻干双拼幼猫粮 2kg',
    categoryId: 14,
    categoryName: '国产猫粮',
    brandId: 8,
    brandName: '麦富迪',
    price: 128.0,
    stock: 200,
    status: 1,
    detail: {
      description: '麦富迪冻干双拼幼猫粮，颗粒适合幼猫，支持生长发育。',
      specJson: { 净含量: '2kg', 适用: '幼猫', 产地: '中国' }
    }
  },
  {
    productId: 22,
    title: '冻干鸡肉粒猫狗通用零食桶 500g',
    categoryId: 15,
    categoryName: '冻干猫粮',
    brandId: 8,
    brandName: '麦富迪',
    price: 89.0,
    stock: 300,
    status: 1,
    detail: {
      description: '冻干鸡肉粒零食桶，猫狗通用，可作训练奖励或拌粮。',
      specJson: { 净含量: '500g', 类型: '冻干零食', 产地: '中国' }
    }
  },
  {
    productId: 23,
    title: '路斯奶酪牛肉棒狗零食 200g',
    categoryId: 16,
    categoryName: '零食',
    brandId: 17,
    brandName: '路斯',
    price: 18.9,
    stock: 400,
    status: 1,
    detail: {
      description: '路斯奶酪牛肉棒，干燥有嚼劲，磨牙解馋，训练奖励。',
      specJson: { 净含量: '200g', 类型: '狗零食', 产地: '中国' }
    }
  },
  {
    productId: 24,
    title: '顽皮醇香牛肉棒狗零食 400g',
    categoryId: 16,
    categoryName: '零食',
    brandId: 18,
    brandName: '顽皮',
    price: 29.9,
    stock: 350,
    status: 1,
    detail: {
      description: '顽皮醇香牛肉棒，多道工序加工，低温风干，醇香美味。',
      specJson: { 净含量: '400g', 类型: '狗零食', 产地: '中国' }
    }
  },
  {
    productId: 25,
    title: '拜耳拜宠清犬用体内驱虫药 6片装',
    categoryId: 17,
    categoryName: '医疗保健',
    brandId: 10,
    brandName: '拜耳',
    price: 109.0,
    stock: 120,
    status: 1,
    detail: {
      description: '拜耳拜宠清犬用体内驱虫，正规兽药批文，用于2kg以上宠物犬。',
      specJson: { 规格: '6片/盒', 类型: '体内驱虫', 产地: '德国' }
    }
  },
  {
    productId: 26,
    title: '犬心保驱虫牛肉块 S号 6粒/盒',
    categoryId: 17,
    categoryName: '医疗保健',
    brandId: 16,
    brandName: '犬心保',
    price: 129.0,
    stock: 90,
    status: 1,
    detail: {
      description: '犬心保牛肉块驱虫，每月一次，驱除蛔虫心丝虫钩虫，牛肉粒易喂食。',
      specJson: { 规格: 'S号 6粒', 适用体重: '11kg以下', 产地: '美国' }
    }
  },
  {
    productId: 27,
    title: '卫仕犬猫通用营养膏 120g',
    categoryId: 18,
    categoryName: '营养保健',
    brandId: 9,
    brandName: '卫仕',
    price: 59.0,
    stock: 260,
    status: 1,
    detail: {
      description: '卫仕营养膏，犬猫通用，产后病后营养补充，能量补给。',
      specJson: { 净含量: '120g', 类型: '营养膏', 产地: '中国' }
    }
  },
  {
    productId: 28,
    title: '红狗犬用营养膏 120g',
    categoryId: 18,
    categoryName: '营养保健',
    brandId: 20,
    brandName: '红狗',
    price: 45.0,
    stock: 310,
    status: 1,
    detail: {
      description: '红狗犬用营养膏，易消化高能量，适合体弱或术后恢复。',
      specJson: { 净含量: '120g', 类型: '营养膏', 产地: '中国' }
    }
  },
  {
    productId: 29,
    title: '小佩智能饮水机三代 1.35L',
    categoryId: 19,
    categoryName: '生活日用',
    brandId: 11,
    brandName: '小佩',
    price: 298.0,
    stock: 85,
    status: 1,
    detail: {
      description: '小佩智能饮水机三代，四重净化，循环活水，防干烧保护。',
      specJson: { 容量: '1.35L', 类型: '智能饮水', 产地: '中国' }
    }
  },
  {
    productId: 30,
    title: '爱丽思密封储粮桶 MFS-10 大号约装10kg',
    categoryId: 19,
    categoryName: '生活日用',
    brandId: 19,
    brandName: '爱丽思',
    price: 99.0,
    stock: 140,
    status: 1,
    detail: {
      description: '爱丽思密封储粮桶，可装约10kg干粮，送勺，内置干燥剂存储位。',
      specJson: { 型号: 'MFS-10', 颜色: '绿盖', 产地: '中国' }
    }
  },
  {
    productId: 31,
    title: '尼龙反光胸背带牵引套装 大型犬 L码',
    categoryId: 20,
    categoryName: '牵引出行',
    brandId: null,
    brandName: null,
    price: 68.0,
    stock: 175,
    status: 1,
    detail: {
      description: '尼龙反光胸背带牵引套装，夜间反光条，分散拉力，适合大型犬。',
      specJson: { 尺码: 'L', 类型: '胸背带+牵引绳', 产地: '中国' }
    }
  },
  {
    productId: 32,
    title: '麦富迪冻干三文鱼全价犬粮 2kg 无谷',
    categoryId: 12,
    categoryName: '冻干狗粮',
    brandId: 8,
    brandName: '麦富迪',
    price: 168.0,
    stock: 110,
    status: 1,
    detail: {
      description: '麦富迪冻干三文鱼全价犬粮，无谷配方，富含Omega-3，呵护皮肤与被毛。',
      specJson: { 净含量: '2kg', 主要成分: '三文鱼', 产地: '中国' }
    }
  },
  {
    productId: 33,
    title: '比瑞吉冻干鸡胸肉猫咪零食 200g',
    categoryId: 15,
    categoryName: '冻干猫粮',
    brandId: 3,
    brandName: '比瑞吉',
    price: 45.0,
    stock: 240,
    status: 1,
    detail: {
      description: '比瑞吉冻干鸡胸肉猫零食，单肉源低敏，复水可拌粮。',
      specJson: { 净含量: '200g', 类型: '冻干猫零食', 产地: '中国' }
    }
  },
  {
    productId: 34,
    title: '可伸缩尼龙牵引绳 5m 中型犬用',
    categoryId: 20,
    categoryName: '牵引出行',
    brandId: null,
    brandName: null,
    price: 42.0,
    stock: 320,
    status: 1,
    detail: {
      description: '可伸缩尼龙牵引绳，5米绳长，带制动锁止，适合中型犬日常遛弯。',
      specJson: { 长度: '5m', 类型: '伸缩牵引', 产地: '中国' }
    }
  }
]

// 离线开发用示例评论
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

// 离线开发用示例分类
export const mockCategories = [
  { categoryId: 1, parentId: 0, name: '狗粮', path: '1' },
  { categoryId: 2, parentId: 0, name: '猫粮', path: '2' },
  { categoryId: 4, parentId: 1, name: '幼犬粮', path: '1/4' },
  { categoryId: 5, parentId: 1, name: '成犬粮', path: '1/5' },
  { categoryId: 6, parentId: 2, name: '幼猫粮', path: '2/6' },
  { categoryId: 7, parentId: 2, name: '成猫粮', path: '2/7' },
  { categoryId: 21, parentId: 0, name: '宠物零食', path: '21' },
  { categoryId: 22, parentId: 0, name: '宠物玩具', path: '22' },
  { categoryId: 23, parentId: 0, name: '清洁护理', path: '23' },
  { categoryId: 24, parentId: 0, name: '宠物医疗', path: '24' },
  { categoryId: 25, parentId: 0, name: '宠物营养', path: '25' },
  { categoryId: 26, parentId: 0, name: '生活日用', path: '26' },
  { categoryId: 27, parentId: 0, name: '牵引出行', path: '27' },
  { categoryId: 8, parentId: 22, name: '玩具', path: '22/8' },
  { categoryId: 9, parentId: 23, name: '清洁用品', path: '23/9' },
  { categoryId: 10, parentId: 1, name: '进口狗粮', path: '1/10' },
  { categoryId: 11, parentId: 1, name: '国产狗粮', path: '1/11' },
  { categoryId: 12, parentId: 1, name: '冻干狗粮', path: '1/12' },
  { categoryId: 13, parentId: 2, name: '进口猫粮', path: '2/13' },
  { categoryId: 14, parentId: 2, name: '国产猫粮', path: '2/14' },
  { categoryId: 15, parentId: 2, name: '冻干猫粮', path: '2/15' },
  { categoryId: 16, parentId: 21, name: '零食', path: '21/16' },
  { categoryId: 17, parentId: 24, name: '医疗保健', path: '24/17' },
  { categoryId: 18, parentId: 25, name: '营养保健', path: '25/18' },
  { categoryId: 19, parentId: 26, name: '生活日用', path: '26/19' },
  { categoryId: 20, parentId: 27, name: '牵引出行', path: '27/20' }
]

// 离线开发用示例购物车
export const mockCart = [
  { cartId: 1, userId: 1, productId: 1, quantity: 2 },
  { cartId: 2, userId: 1, productId: 8, quantity: 1 }
]
