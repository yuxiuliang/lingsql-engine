package com.lingsql.employee.repository;

import com.lingsql.employee.entity.DigitalEmployee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 数字员工仓库。
 *
 * @author 余炘洋
 * @date 2026-02-28
 */
public interface DigitalEmployeeRepository extends JpaRepository<DigitalEmployee, Long> {

    /**
     * 按删除状态查询数字员工列表。
     *
     * @param isDeleted 删除标记
     * @return 数字员工列表
     * @author 余炘洋
     * @date 2026-02-28
     */
    List<DigitalEmployee> findByIsDeleted(Integer isDeleted);

    /**
     * 查询启用且已开启定时的数字员工。
     *
     * @param isDeleted       删除标记
     * @param status          状态
     * @param scheduleEnabled 定时开关
     * @return 数字员工列表
     * @author 余炘洋
     * @date 2026-04-14
     */
    List<DigitalEmployee> findByIsDeletedAndStatusAndScheduleEnabled(Integer isDeleted, Integer status, Integer scheduleEnabled);

    /**
     * 按主键与删除状态查询数字员工。
     *
     * @param id        主键ID
     * @param isDeleted 删除标记
     * @return 数字员工
     * @author 余炘洋
     * @date 2026-02-28
     */
    Optional<DigitalEmployee> findByIdAndIsDeleted(Long id, Integer isDeleted);

    /**
     * 判断同租户员工编码是否重复。
     *
     * @param tenantId     租户ID
     * @param employeeCode 员工编码
     * @param isDeleted    删除标记
     * @return 是否存在
     * @author 余炘洋
     * @date 2026-02-28
     */
    boolean existsByTenantIdAndEmployeeCodeAndIsDeleted(String tenantId, String employeeCode, Integer isDeleted);
}

