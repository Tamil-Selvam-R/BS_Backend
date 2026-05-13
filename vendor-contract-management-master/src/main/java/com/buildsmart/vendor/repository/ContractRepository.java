package com.buildsmart.vendor.repository;

import com.buildsmart.vendor.enums.ContractStatus;
import com.buildsmart.vendor.entity.Contract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ContractRepository extends JpaRepository<Contract, String> {
    List<Contract> findByVendorId(String vendorId);
    List<Contract> findByStatus(ContractStatus status);
    Optional<Contract> findTopByOrderByContractIdDesc();

    /**
     * Distinct list of vendor IDs known to this service.
     * Used by the derived /api/vendors endpoint consumed by analytics/buildsmart-report.
     * The vendor-service has no Vendor master entity — vendors live only as
     * vendorId references on contracts — so the analytics need is satisfied by
     * deriving the list from contract data.
     */
    @Query("SELECT DISTINCT c.vendorId FROM Contract c WHERE c.vendorId IS NOT NULL")
    List<String> findDistinctVendorIds();

    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM Contract c " +
           "WHERE c.vendorId = :vendorId AND c.projectId = :projectId " +
           "AND ((:taskId IS NULL AND c.taskId IS NULL) OR c.taskId = :taskId) " +
           "AND c.startDate = :startDate AND c.endDate = :endDate " +
           "AND c.value = :value")
    boolean existsDuplicate(@Param("vendorId") String vendorId,
                            @Param("projectId") String projectId,
                            @Param("taskId") String taskId,
                            @Param("startDate") java.time.LocalDate startDate,
                            @Param("endDate") java.time.LocalDate endDate,
                            @Param("value") java.math.BigDecimal value);
}
