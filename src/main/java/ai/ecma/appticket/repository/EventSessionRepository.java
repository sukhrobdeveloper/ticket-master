package ai.ecma.appticket.repository;

import ai.ecma.appticket.entity.EventSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.transaction.Transactional;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventSessionRepository extends JpaRepository<EventSession, UUID> {

    Optional<EventSession> findByIdAndActiveTrue(UUID id);

    List<EventSession> findAllByEventId(UUID event_id);
    /**
     * Bu SQL so'rov yaratayotgan e.sessionni boshlanish vaqti va tugash vaqtini aynan biz yaratayotgan e.sessionni
     * manzilidagi e.sessionlarni boshlanish va tugash vaqtiga tekshirib chiqadi.
     * Agar yangi e.session ning vaqtlari boshqa e.sessionni vaqtlariga tugri kelib qolsa ularni listga yigamiz
     * va shu listni return(qaytarib) qilib yuboramiz.
     * @param addressId biz yaratayotgan e.session utkaziladigan manzil
     * @param startTime biz yaratayotgan e.session ning boshlanish vaqti
     * @param endTime biz yaratayotgan e.session ning tugash vaqti
     * @return List<EventSession>
     *
     */
    @Query(value = "select * from event_session es\n" +
            "join event e on es.event_id = e.id\n" +
            "where e.address_id=:addressId and es.deleted=false and e.deleted=false\n" +
            "and (:startTime between es.start_time and es.end_time\n" +
            "    or :endTime between es.start_time and es.end_time\n" +
            "    or es.start_time between :startTime and :endTime\n" +
            "    or es.end_time between :startTime and :endTime) and active", nativeQuery = true)
    List<EventSession> findAllTimeConflict(@Param("addressId") UUID addressId,
                                           @Param("startTime") Timestamp startTime,
                                           @Param("endTime") Timestamp endTime);

    /**
     * Bu SQL so'rovda ham tepadagi holatlarga tekshiramiz yani o'zgartirayotgan vaqtimiz boshqa
     * e.session larni vaqtiga to'g'ri kelmayotganiga tekshiramiz va aynan uzini id siga ham
     * teng bo'lib qolmasligiga tekshiramiz
     * @param addressId o'zgartiradigan manzilimiz id si
     * @param id o'zgartirayotgan e.sessionimizni id si
     * @param startTime o'zgartirayotgan boshlanish vaqt
     * @param endTime o'zgartirayotgan tugash vaqt
     * @return List<EventSession>
     */
    @Query(value = "select * from event_session es\n" +
            "join event e on es.event_id = e.id\n" +
            "where e.address_id=:addressId and es.deleted=false and e.deleted=false and es.id != :id\n" +
            "and (:startTime between es.start_time and es.end_time\n" +
            "    or :endTime between es.start_time and es.end_time\n" +
            "    or es.start_time between :startTime and :endTime\n" +
            "    or es.end_time between :startTime and :endTime) and active", nativeQuery = true)
    List<EventSession> findAllTimeConflictIdNot(@Param("addressId") UUID addressId,
                                                @Param("id") UUID id,
                                                @Param("startTime") Timestamp startTime,
                                                @Param("endTime") Timestamp endTime);

    /**
     * Bu sql so'rov bizga uuid tipidagi id larni string ugirib olib beradi. Ya'ni e.sessiyani tugash vaqti
     * biz bergan vaqtdan  kichig bulganlarni oladi.
     * @param currentTime bu biz beradigan vaqt
     * @return uuid tipdagi id larni string ga ugirib list qaytaradi.
     */
    @Query(value = "select cast (id as varchar ) from event_session where end_time<:currentTime",
            nativeQuery = true)
    List<String> findAllByExpiredEventSessions(@Param("currentTime") Timestamp currentTime);


    @Modifying
    @Transactional
    @Query(value = "update event_session set active=false where id in :ids", // indan keyin doim probel qoldiring
            nativeQuery = true)
    Integer updateActiveFieldFalse(@Param("ids") List<UUID> ids);

}
