package com.s2admin.module.system.service;

import com.s2admin.module.common.PageResult;
import com.s2admin.module.common.exception.BusinessException;
import com.s2admin.module.tenant.TenantContext;
import com.s2admin.module.system.entity.SysFile;
import com.s2admin.module.system.form.FileQuery;
import com.s2admin.module.system.repository.SysFileRepository;
import com.s2admin.module.system.storage.FileStorage;
import com.s2admin.module.system.vo.FileVO;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileService {

    private static final Set<String> DEFAULT_TYPES = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "pdf", "doc", "docx", "xls", "xlsx", "csv", "zip");

    private final FileStorage fileStorage;
    private final SysFileRepository fileRepository;
    private final DataScopeService dataScopeService;

    public FileVO upload(MultipartFile file) {
        return upload(file, "default");
    }

    @Transactional
    public FileVO upload(MultipartFile file, String category) {
        if ("avatar".equalsIgnoreCase(category)) {
            throw new BusinessException("头像请在个人中心上传");
        }
        return storeUpload(file, category);
    }

    @Transactional
    public FileVO uploadAvatar(MultipartFile file) {
        validateImage(file);
        return storeUpload(file, "avatar");
    }

    private FileVO storeUpload(MultipartFile file, String category) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择文件");
        }
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        int slash = Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\'));
        if (slash >= 0 && slash < original.length() - 1) {
            original = original.substring(slash + 1);
        }
        String ext = extension(original);
        if (!DEFAULT_TYPES.contains(ext)) {
            throw new BusinessException("不支持的文件类型: " + ext);
        }
        String stored = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        FileStorage.Stored saved = fileStorage.store(file, stored);
        SysFile meta = new SysFile();
        meta.setOriginalName(original);
        meta.setStoredName(saved.storedName());
        meta.setUrl(saved.url());
        meta.setContentType(contentTypeFromExt(ext));
        meta.setSize(file.getSize());
        meta.setCategory(StringUtils.hasText(category) ? category : "default");
        meta.setStorageType(fileStorage.type());
        Long tenantLimit = TenantContext.get();
        if (tenantLimit != null && tenantLimit > 0) {
            meta.setTenantId(tenantLimit);
        }
        if (meta.getCreateBy() == null) {
            try {
                meta.setCreateBy(com.s2admin.module.common.util.SecurityUtils.getUserId());
            } catch (Exception ignored) {
            }
        }
        fileRepository.save(meta);
        return toVO(meta);
    }

    @Transactional(readOnly = true)
    public PageResult<FileVO> page(FileQuery query) {
        if (query.getOrderBy() == null) {
            query.setOrderBy("id");
            query.setSortDirection("desc");
        }
        Specification<SysFile> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(query.getKeyword())) {
                String like = "%" + query.getKeyword().trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("originalName")), like));
            }
            if (StringUtils.hasText(query.getCategory())) {
                predicates.add(cb.equal(root.get("category"), query.getCategory()));
            }
            dataScopeService.applyOwner(root, "createBy", cb, predicates);
            Long tenantLimit = TenantContext.get();
            if (tenantLimit != null) {
                predicates.add(cb.equal(root.get("tenantId"), tenantLimit));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<SysFile> page = fileRepository.findAll(spec, query.toPageable());
        return PageResult.of(page, this::toVO);
    }

    public Resource load(String filename) {
        SysFile meta = fileRepository.findByStoredName(filename)
                .orElseThrow(() -> BusinessException.notFound("文件不存在"));
        Long tenantLimit = TenantContext.get();
        if (tenantLimit != null && meta.getTenantId() != null && !tenantLimit.equals(meta.getTenantId())) {
            throw BusinessException.notFound("文件不存在");
        }
        String type = contentTypeFromExt(extension(filename));
        boolean publicAvatar = "avatar".equalsIgnoreCase(meta.getCategory()) && type.startsWith("image/");
        if (!publicAvatar) {
            if (!com.s2admin.module.common.util.SecurityUtils.hasPermission("system:file:view")) {
                throw BusinessException.forbidden("没有操作权限");
            }
            dataScopeService.assertCanAccessOwner(meta.getCreateBy());
        }
        return fileStorage.load(filename);
    }

    public String contentType(String filename) {
        return contentTypeFromExt(extension(filename));
    }

    public static boolean previewable(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return false;
        }
        return contentType.startsWith("image/") || MediaType.APPLICATION_PDF_VALUE.equals(contentType);
    }

    private String contentTypeFromExt(String ext) {
        return switch (ext) {
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG_VALUE;
            case "png" -> MediaType.IMAGE_PNG_VALUE;
            case "gif" -> MediaType.IMAGE_GIF_VALUE;
            case "webp" -> "image/webp";
            case "pdf" -> MediaType.APPLICATION_PDF_VALUE;
            case "csv" -> "text/csv";
            case "zip" -> "application/zip";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default -> MediaType.APPLICATION_OCTET_STREAM_VALUE;
        };
    }

    @Transactional
    public void delete(String filename) {
        SysFile file = fileRepository.findByStoredName(filename)
                .orElseThrow(() -> BusinessException.notFound("文件不存在"));
        dataScopeService.assertCanAccessOwner(file.getCreateBy());
        fileStorage.delete(file.getStoredName());
        fileRepository.delete(file);
    }

    @Transactional
    public void deleteById(Long id) {
        SysFile file = fileRepository.findById(id).orElseThrow(() -> BusinessException.notFound("文件不存在"));
        dataScopeService.assertCanAccessOwner(file.getCreateBy());
        fileStorage.delete(file.getStoredName());
        fileRepository.delete(file);
    }

    public void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择文件");
        }
        String ext = extension(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        if (!Set.of("jpg", "jpeg", "png", "gif", "webp").contains(ext)) {
            throw new BusinessException("头像仅支持 jpg/png/gif/webp");
        }
    }

    private FileVO toVO(SysFile file) {
        FileVO vo = new FileVO();
        vo.setId(file.getId());
        vo.setUrl(file.getUrl());
        vo.setName(file.getOriginalName());
        vo.setStoredName(file.getStoredName());
        vo.setSize(file.getSize() == null ? 0 : file.getSize());
        vo.setCategory(file.getCategory());
        vo.setContentType(file.getContentType());
        vo.setStorageType(file.getStorageType());
        vo.setCreateTime(file.getCreateTime());
        return vo;
    }

    private String extension(String name) {
        int i = name.lastIndexOf('.');
        if (i < 0 || i == name.length() - 1) {
            return "";
        }
        return name.substring(i + 1).toLowerCase(Locale.ROOT);
    }
}
