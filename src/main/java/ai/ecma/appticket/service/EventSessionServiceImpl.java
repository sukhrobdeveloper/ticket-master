package ai.ecma.appticket.service;

import ai.ecma.appticket.entity.*;
import ai.ecma.appticket.exception.RestException;
import ai.ecma.appticket.mapper.CustomMapper;
import ai.ecma.appticket.payload.*;
import ai.ecma.appticket.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventSessionServiceImpl implements EventSessionService {

    private final EventSessionRepository eventSessionRepository;
    private final EventRepository eventRepository;
    private final SeatTemplateRepository seatTemplateRepository;
    private final TicketRepository ticketRepository;

    @Override
    public ApiResult<CustomPage<EventSessionResDto>> getAll(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<EventSession> sessionPage = eventSessionRepository.findAll(pageable);
        CustomPage<EventSessionResDto> customPage = eventSessionToCustomPage(sessionPage);
        return ApiResult.successResponse(customPage);
    }

    @Override
    public ApiResult<EventSessionResDto> getOne(UUID id) {
        EventSession session = eventSessionRepository.findById(id).orElseThrow(() -> new RestException(HttpStatus.NOT_FOUND, "Tadbir sessiyasi topilmadi"));
        return ApiResult.successResponse(CustomMapper.eventSessionToDto(session));
    }

    /**
     * Bu "getByEventId" method eventSession ni  Event id bo'yicha olish. Ya'ni Id si kirib kelayotgan eventga
     * tegishli bo'lgan eventSessionlarni olish uchun yaratildi.
     * @param eventId - eventId orqali shu eventga tegishli bo'lgan e.s larni olamiz
     * @return EventSessionResDto
     */
    @Override
    public ApiResult<List<EventSessionResDto>> getByEventId(UUID eventId) {
        List<EventSession> sessions = eventSessionRepository.findAllByEventId(eventId);
        List<EventSessionResDto> list = sessions.stream().map(CustomMapper::eventSessionToDto).collect(Collectors.toList());
        return ApiResult.successResponse(list);
    }

    /**
     * Bu "add" methodida biz eventSession yaratamiz. Tadbir(Event) o'tkaziladigan address da
     * shu yaratilayotgan e.s ni boshlanish vaqti(startTime)
     * va tugash vaqti(endTime)ni tekshiramiz yani aynan shu vaqtlar qaysidir e.s ning vaqt oraligiga tushib qolmaganini
     * va qaysidir e.s bizning bergan vaqt oraligiga tushub qolmaganiga. Hammasi yaxshi bulsa
     * yangi e.s yaratamiz. E.S yaratilganda shu e.s ga tegishli bulgan biletlarni(tiskets) ham
     * yaratib ketamiz.
     * Birinchi urinda biz yaratayotgan  e.session ning boshlanish vaqti tugash vaqtidan
     * oldinligini tekshiramiz va boshlanish vaqti hozirdan yani e.session yaratilayotgan vaqtdan keyin ekanligiga
     * tekshiramiz. Agar unisi yoki bunisi false bulsa throw ga otib yuboramiz.
     * @param eventSessionReqDto - Dto da biz qo'shmoqchi bo'lgan fieldlar keladi
     * @return EventSessionResDto
     */
    @Override
    public ApiResult<EventSessionResDto> add(EventSessionReqDto eventSessionReqDto) {
        if (eventSessionReqDto.getStartTime().after(eventSessionReqDto.getEndTime())
        || eventSessionReqDto.getStartTime().before(new Timestamp(System.currentTimeMillis())))
            throw new RestException(HttpStatus.BAD_REQUEST, "Vaqtni to'g'ri kiriting");
        Event event = eventRepository.findById(eventSessionReqDto.getEventId()).
                orElseThrow(() -> new RestException(HttpStatus.NOT_FOUND, "Tadbir topilmadi"));
        List<EventSession> conflictSession = eventSessionRepository.findAllTimeConflict(
                event.getAddress().getId(),
                eventSessionReqDto.getStartTime(),
                eventSessionReqDto.getEndTime()
        );
        if (!conflictSession.isEmpty())
            throw new RestException(
                    HttpStatus.CONFLICT,
                    "Bu vaqtda tadbir bor",
                    conflictSession.stream().map(CustomMapper::eventSessionToDto).collect(Collectors.toSet())
            );
        EventSession eventSession = new EventSession(
                event,
                eventSessionReqDto.getStartTime(),
                eventSessionReqDto.getEndTime()
        );
        eventSessionRepository.save(eventSession);

        SeatTemplate seatTemplate = seatTemplateRepository.findById(eventSessionReqDto.getSeatTemplateId()).orElseThrow(
                () -> new RestException(HttpStatus.NOT_FOUND, "template topilmadi"));
        List<Ticket> ticketList = seatTemplate.getSeatTemplateChairList().stream().map(
                seatTemplateChair -> new Ticket(
                        null,
                        eventSession,
                        seatTemplateChair.getSection(),
                        seatTemplateChair.getRow(),
                        seatTemplateChair.getName(),
                        seatTemplateChair.getStatus(),
                        seatTemplateChair.getPrice()
                )
        ).collect(Collectors.toList());
        ticketRepository.saveAll(ticketList);
        return ApiResult.successResponse(CustomMapper.eventSessionToDto(eventSession));
    }

    /**
     * Bu "edit" methodda e.s ni o'zgartiramiz. O'zgartirayotganimizda id ni, boshlanish(startTime) va tugash(endTime) vaqtini,
     * manzilini(address) tekshirib chiqamiz. Hammasi yaxshi bo'lsa o'zgartirib saqlab qo'yamiz.
     * @param eventSessionReqDto - Dto da biz o'zgartirmoqchi bo'lgan fieldlar keladi
     * @param id- Id orqali o'zgartirmoqchi bo'lgan e.s ni olamiz
     * @return EventSessionResDto
     */
    @Override
    public ApiResult<EventSessionResDto> edit(EventSessionReqDto eventSessionReqDto, UUID id) {
        if (eventSessionReqDto.getStartTime().after(eventSessionReqDto.getEndTime()))
            throw new RestException(HttpStatus.BAD_REQUEST, "Vaqtni to'g'ri kiriting");
        Event event = eventRepository.findById(eventSessionReqDto.getEventId()).
                orElseThrow(() -> new RestException(HttpStatus.NOT_FOUND, "Tadbir topilmadi"));
        EventSession eventSession = eventSessionRepository.findById(id)
                .orElseThrow(() -> new RestException(HttpStatus.NOT_FOUND, "Tadbir sessiyasi topilmadi"));
        List<EventSession> conflictIdNot = eventSessionRepository.findAllTimeConflictIdNot(
                eventSession.getEvent().getAddress().getId(),
                id,
                eventSessionReqDto.getStartTime(),
                eventSessionReqDto.getEndTime()
        );

        if (!conflictIdNot.isEmpty())
            throw new RestException(
                    HttpStatus.CONFLICT,
                    "Bu vaqtda tadbir bor",
                    conflictIdNot.stream().map(CustomMapper::eventSessionToDto).collect(Collectors.toSet())
            );
        eventSession.setEvent(event);
        eventSession.setStartTime(eventSessionReqDto.getStartTime());
        eventSession.setEndTime(eventSessionReqDto.getEndTime());
        eventSessionRepository.save(eventSession);
        return ApiResult.successResponse(CustomMapper.eventSessionToDto(eventSession));
    }

    @Override
    public ApiResult<?> delete(UUID id) {
        try {
            eventSessionRepository.deleteById(id);
            return ApiResult.successResponse("Tadbir sessiyasi o'chirildi");
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new RestException(HttpStatus.NOT_FOUND, "Topilmadi");
        }
    }

    private CustomPage<EventSessionResDto> eventSessionToCustomPage(Page<EventSession> eventSessionPage) {
        return new CustomPage<>(
                eventSessionPage.getContent().stream().map(CustomMapper::eventSessionToDto).collect(Collectors.toList()),
                eventSessionPage.getNumberOfElements(),
                eventSessionPage.getNumber(),
                eventSessionPage.getTotalElements(),
                eventSessionPage.getTotalPages(),
                eventSessionPage.getSize()
        );
    }

}
