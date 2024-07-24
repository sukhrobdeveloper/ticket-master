package ai.ecma.appticket.repository;

import ai.ecma.appticket.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.transaction.Transactional;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    boolean existsBySectionAndRowAndNameAndEventSessionId(String section, String row, String name, UUID eventSession_id);

    boolean existsByIdNotAndSectionAndRowAndNameAndEventSessionId(UUID id, String section, String row, String name, UUID eventSession_id);


    @Transactional
    @Modifying
    @Query(value = "UPDATE Ticket SET status=:status WHERE id in :idList",nativeQuery = true)
    Integer updateStatus(@Param("status") String status,@Param("idList") List<UUID> idList);


    //update ,insert ,delete ishlatilganda shu annotatsiyalardan foydalanish shart! | select uchun kerak emas

    /**
     * bu sql so'rov MB dan biz bergan id lik event session ga tegishli bulgan biletlarni
     * topib active ni false qilib quyadi.
     * @param ids biz bergan id lar
     * @return update buldi yoki yuqligini qaytaruvchi Integer qiymat yani 1  bulsa update buldi 0 bulsa
     * update bulmadi.
     */
    @Modifying
    @Transactional
    @Query(value = "update ticket set active=false where event_session_id in :ids",
            nativeQuery = true)
    Integer updateActiveFieldFalse(@Param("ids") List<UUID> ids);


    /**
     * Bu JPA query bizga biz bergan biletlarni id siga tegishli biletlarni va
     * faol(active=true) biletlarni qaytaradi
     * @param id biz berayotgan biletni id si
     * @return biz bergan id ga tegishli biletlarni qaytaradi
     */
    List<Ticket> findAllByIdInAndActiveTrue(Collection<UUID> id);

}
