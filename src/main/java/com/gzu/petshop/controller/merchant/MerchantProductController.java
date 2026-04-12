package com.gzu.petshop.controller.merchant;

import com.gzu.petshop.common.Result;
import com.gzu.petshop.dto.merchant.MerchantProductContentJsonRequest;
import com.gzu.petshop.dto.merchant.MerchantProductCreateRequest;
import com.gzu.petshop.dto.merchant.MerchantProductDTO;
import com.gzu.petshop.dto.merchant.MerchantProductUpdateRequest;
import com.gzu.petshop.service.product.MerchantProductService;
import com.gzu.petshop.service.storage.UploadStorageService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/merchant/products")
@CrossOrigin
public class MerchantProductController {
    private final MerchantProductService merchantProductService;
    private final UploadStorageService uploadStorageService;

    public MerchantProductController(MerchantProductService merchantProductService,
                                     UploadStorageService uploadStorageService) {
        this.merchantProductService = merchantProductService;
        this.uploadStorageService = uploadStorageService;
    }

    @GetMapping
    public Result<List<MerchantProductDTO>> getProducts(@RequestParam Long merchantId) {
        return Result.success(merchantProductService.getProducts(merchantId));
    }

    @GetMapping("/{productId}")
    public Result<MerchantProductDTO> getProduct(@PathVariable Long productId, @RequestParam Long merchantId) {
        MerchantProductDTO dto = merchantProductService.getProduct(merchantId, productId);
        if (dto == null) return Result.error("商品不存在");
        if (dto.getProductId() == null) return Result.error("无权限查看该商品");
        return Result.success(dto);
    }

    @PutMapping("/{productId}")
    public Result<Void> updateProduct(@PathVariable Long productId, @RequestBody MerchantProductUpdateRequest req) {
        String err = merchantProductService.updateProduct(productId, req);
        return err == null ? Result.success() : Result.error(err);
    }

    @PostMapping
    public Result<Map<String, Object>> createProduct(@RequestBody MerchantProductCreateRequest req) {
        Long productId = merchantProductService.createProduct(req);
        if (productId == null) return Result.error("创建失败，请检查参数");
        return Result.success(Map.of("productId", productId));
    }

    /**
     * 更新详情：JSON 请求体（Apifox 选 body → raw → JSON）。
     * 与 {@link #updateContentMultipart} 同路径，按 {@code Content-Type} 分流。
     */
    @PutMapping(value = "/{productId}/content", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result<Void> updateContentJson(@PathVariable Long productId,
                                         @RequestBody(required = false) MerchantProductContentJsonRequest req) {
        String description = req != null ? req.getDescription() : null;
        String specJson = req != null ? req.getSpecJson() : null;
        String imageUrl = req != null ? req.getImageUrl() : null;
        String err = merchantProductService.updateProductContent(productId, description, specJson, imageUrl);
        return err == null ? Result.success() : Result.error(err);
    }

    /**
     * 更新详情：表单上传（与前端 {@code FormData} 一致）。
     * 若携带 {@code imageFile}，先保存到 {@code /uploads} 并以生成路径落库；否则使用 {@code imageUrl} 文本。
     */
    @PutMapping(value = "/{productId}/content", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Void> updateContentMultipart(@PathVariable Long productId,
                                               @RequestParam(required = false) String description,
                                               @RequestParam(required = false) String specJson,
                                               @RequestParam(required = false) String imageUrl,
                                               @RequestParam(required = false) MultipartFile imageFile) {
        String effectiveImageUrl = imageUrl;
        if (imageFile != null && !imageFile.isEmpty()) {
            try {
                effectiveImageUrl = uploadStorageService.storeProductImage(imageFile);
            } catch (IllegalArgumentException e) {
                return Result.error("请上传 jpg/png/gif/webp 等常见图片，且大小不超过限制");
            } catch (Exception e) {
                return Result.error("图片上传失败，请稍后重试");
            }
        }
        String err = merchantProductService.updateProductContent(productId, description, specJson, effectiveImageUrl);
        return err == null ? Result.success() : Result.error(err);
    }
}

