package ai.ecma.appticket.component;

import ai.ecma.appticket.entity.Bron;
import ai.ecma.appticket.entity.Order;
import ai.ecma.appticket.entity.Ticket;
import ai.ecma.appticket.enums.BronStatusEnum;
import ai.ecma.appticket.enums.OrderTypeEnum;
import ai.ecma.appticket.enums.SeatStatusEnum;
import ai.ecma.appticket.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@EnableScheduling
@Component
@RequiredArgsConstructor
public class ScheduleComponent {
    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final BronRepository bronRepository;
    private final BronTariffRepository bronTariffRepository;
    private final EventSessionRepository eventSessionRepository;

    @Value(value = "${orderLifeTime}")
    private Long orderLifeTime;

    /**
     * Bu orderRemove methodi har 1 minutda yashash vaqti tugagan buyurtma(order) larni tekshirib turadi agar
     * ular bo'lsa shu orderdagi biletlarni olib statusini VACANT(bo'sh) qilib qoyadi va yana sotuvga chiqariladi
     * Va shu buyurtma o'chiriladi
     */
    @Scheduled(fixedRate = 60000L)
    private void orderRemover() {
        Timestamp pastTime = new Timestamp(System.currentTimeMillis() - orderLifeTime);
        List<Order> orderList = orderRepository.findAllByCreatedAtBeforeAndFinishedFalseAndTypeNot(pastTime, OrderTypeEnum.PAY_AFTER);
        List<UUID> tickedIdList = new ArrayList<>();
        if (!orderList.isEmpty()) {
            for (Order order : orderList) {
                tickedIdList.addAll(Arrays.stream(order.getTickets()).map(UUID::fromString).collect(Collectors.toList()));
            }
            ticketRepository.updateStatus(
                    SeatStatusEnum.VACANT.name(),
                    tickedIdList
            );
        }
        orderRepository.deleteAllByCreatedAtBeforeAndFinishedFalseAndType(pastTime, OrderTypeEnum.PAY_AFTER);
    }

    /**
     * Bu bronRemover methodi har 1 minutda tekshirib ACTIVE bo'lgan va yashash vaqti tugagan bronlarni olib keladi.
     *Bu bronlarni CANCEL(bekor) qiladi va biletni qayta sotuvga chiqaradi
     */
    @Scheduled(fixedRate = 60000L)
    private void bronRemover() {
        List<Bron> bronList = bronRepository.findAllByExpireTimeBeforeAndBronStatusEnum(
                new Timestamp(System.currentTimeMillis()),
                BronStatusEnum.ACTIVE
        );
        List<Ticket> ticketList = new ArrayList<>();
        for (Bron bron : bronList) {
            bron.setBronStatusEnum(BronStatusEnum.CANCEL);
            Ticket ticket = bron.getTicket();
            ticket.setStatus(SeatStatusEnum.VACANT);
            ticketList.add(ticket);
        }
        bronRepository.saveAll(bronList);
        ticketRepository.saveAll(ticketList);
    }

    /**
     * Bu bronTariffDisabler methodi har 1 minutda tekshirib vaqti tugagan bronlarni disable qilib quyadi yani
     * bronning tugash vaqti hozirdan kichik bulsa demak u disable true buladi va bron qilib bulmaydi;
     */
    @Scheduled(fixedRate = 60000L)
    private void bronTariffDisabler() {
        bronTariffRepository.disableBronTariff(new Timestamp(System.currentTimeMillis()));
    }


    /**
     * Bu removeOldTicketAfterSomeTime methodi MB dan id larni stringda olib kn uuid ga ugiradi va
     * O'tib ketgan event session ga tegishli bo'lgan biletlarni uchirib qo'yadi.
     * va shi id ga tegishli bo'lgan event session ni ham uchiradi
     */
    @Scheduled(fixedRate = 60000L)
    private void removeOldTicketAfterSomeTime() {
        List<String> expiredIds = eventSessionRepository.findAllByExpiredEventSessions(new Timestamp(System.currentTimeMillis()));
        if (expiredIds.isEmpty())
            return;
        List<UUID> uuidList = expiredIds.stream().map(UUID::fromString).collect(Collectors.toList());
        ticketRepository.updateActiveFieldFalse(uuidList);
        eventSessionRepository.updateActiveFieldFalse(uuidList);
    }
}
