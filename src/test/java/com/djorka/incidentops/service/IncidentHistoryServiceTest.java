package com.djorka.incidentops.service;

import com.djorka.incidentops.dto.IncidentHistoryResponse;
import com.djorka.incidentops.exception.ResourceNotFoundException;
import com.djorka.incidentops.model.Incident;
import com.djorka.incidentops.model.IncidentAction;
import com.djorka.incidentops.model.IncidentHistory;
import com.djorka.incidentops.model.User;
import com.djorka.incidentops.repository.IncidentHistoryRepository;
import com.djorka.incidentops.repository.IncidentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentHistoryServiceTest {

    @Mock
    private IncidentHistoryRepository historyRepository;
    @Mock
    private IncidentRepository incidentRepository;
    @InjectMocks
    private IncidentHistoryService historyService;

    @Test
    void recordsEventWithTimestampAndActor() {
        Incident incident = Incident.builder().id(3L).build();
        User actor = User.builder().id(5L).name("Operator").build();

        historyService.recordEvent(incident, IncidentAction.STATUS_CHANGED, "OPEN", "INVESTIGATING", actor);

        ArgumentCaptor<IncidentHistory> captor = ArgumentCaptor.forClass(IncidentHistory.class);
        verify(historyRepository).save(captor.capture());
        IncidentHistory saved = captor.getValue();
        assertThat(saved.getIncident()).isSameAs(incident);
        assertThat(saved.getAction()).isEqualTo(IncidentAction.STATUS_CHANGED);
        assertThat(saved.getOldValue()).isEqualTo("OPEN");
        assertThat(saved.getNewValue()).isEqualTo("INVESTIGATING");
        assertThat(saved.getPerformedBy()).isSameAs(actor);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void returnsMappedHistoryIncludingNullableActor() {
        Incident incident = Incident.builder().id(3L).build();
        User actor = User.builder().id(5L).name("Operator").build();
        LocalDateTime firstTime = LocalDateTime.now().minusMinutes(2);
        LocalDateTime secondTime = LocalDateTime.now().minusMinutes(1);
        IncidentHistory first = IncidentHistory.builder()
                .id(1L).incident(incident).action(IncidentAction.CREATED)
                .newValue("OPEN").performedBy(actor).createdAt(firstTime).build();
        IncidentHistory second = IncidentHistory.builder()
                .id(2L).incident(incident).action(IncidentAction.ASSIGNED)
                .newValue("System").performedBy(null).createdAt(secondTime).build();
        when(incidentRepository.existsById(3L)).thenReturn(true);
        when(historyRepository.findByIncidentIdOrderByCreatedAtAsc(3L)).thenReturn(List.of(first, second));

        List<IncidentHistoryResponse> response = historyService.getHistoryByIncident(3L);

        assertThat(response).hasSize(2);
        assertThat(response.get(0).performedByUserId()).isEqualTo(5L);
        assertThat(response.get(0).performedByUserName()).isEqualTo("Operator");
        assertThat(response.get(1).performedByUserId()).isNull();
        assertThat(response.get(1).performedByUserName()).isNull();
        assertThat(response).extracting(IncidentHistoryResponse::createdAt)
                .containsExactly(firstTime, secondTime);
    }

    @Test
    void rejectsHistoryForUnknownIncident() {
        when(incidentRepository.existsById(404L)).thenReturn(false);

        assertThatThrownBy(() -> historyService.getHistoryByIncident(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Incident not found with id: 404");
        verify(historyRepository, never()).findByIncidentIdOrderByCreatedAtAsc(404L);
    }
}
