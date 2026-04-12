package com.gzu.petshop.mapper.admin;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gzu.petshop.entity.AdminAuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AdminAuditLogMapper extends BaseMapper<AdminAuditLog> {
}
