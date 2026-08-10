package com.fabpilot.mescore.alarm;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fabpilot.mescore.alarm.dto.*;
import com.fabpilot.mescore.alarm.exception.*;
import com.fabpilot.mescore.alarm.mapper.*;
import com.fabpilot.mescore.alarm.model.*;
import com.fabpilot.mescore.alarm.service.impl.EquipmentAlarmServiceImpl;
import com.fabpilot.mescore.common.command.CommandExecutionSupport;
import com.fabpilot.mescore.equipment.mapper.EquipmentMapper;
import com.fabpilot.mescore.equipment.model.Equipment;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EquipmentAlarmServiceImplTest {
 @Mock EquipmentAlarmMapper alarmMapper; @Mock EquipmentAlarmActionHistoryMapper historyMapper;
 @Mock EquipmentMapper equipmentMapper; @Mock CommandExecutionSupport command;
 @InjectMocks EquipmentAlarmServiceImpl service; @Mock EquipmentAlarm alarm;
 private AlarmActionRequestTO request;
 @BeforeAll static void metadata(){var a=new MapperBuilderAssistant(new MybatisConfiguration(),"alarm-test");TableInfoHelper.initTableInfo(a,EquipmentAlarm.class);TableInfoHelper.initTableInfo(a,EquipmentAlarmActionHistory.class);TableInfoHelper.initTableInfo(a,Equipment.class);}
 @BeforeEach void setup(){request=new AlarmActionRequestTO();request.setAlarmId(10L);request.setAction("ACKNOWLEDGE");request.setExpectedVersion(0L);request.setIdempotencyKey("ALARM-ACK-001");request.setOperatorId("ENG-001");when(alarmMapper.selectById(10L)).thenReturn(alarm);lenient().when(alarm.getId()).thenReturn(10L);lenient().when(alarm.getEquipmentId()).thenReturn(5L);lenient().when(alarm.getVersion()).thenReturn(0L);}
 @Test void acknowledgeActiveAlarmAndAppendHistory(){when(historyMapper.selectOne(any())).thenReturn(null);when(alarm.getStatus()).thenReturn("ACTIVE");when(command.nextVersion(0L)).thenReturn(1L);when(alarmMapper.update(any(),any())).thenReturn(1);AlarmActionResultTO r=service.executeAction(request);assertThat(r.getStatus()).isEqualTo("ACKNOWLEDGED");assertThat(r.getVersion()).isEqualTo(1L);verify(historyMapper).insert(any(EquipmentAlarmActionHistory.class));}
 @Test void closeRequiresRecoveredEquipment(){request.setAction("CLOSE");when(historyMapper.selectOne(any())).thenReturn(null);when(alarm.getStatus()).thenReturn("ACKNOWLEDGED");Equipment e=mock(Equipment.class);when(equipmentMapper.selectById(5L)).thenReturn(e);when(e.getUpDownStatus()).thenReturn("D");assertThatThrownBy(()->service.executeAction(request)).isInstanceOfSatisfying(AlarmCommandException.class,x->assertThat(x.getErrorCode()).isEqualTo(AlarmCommandErrorCode.EQUIPMENT_NOT_RECOVERED));verify(alarmMapper,never()).update(any(),any());}
 @Test void closeAcknowledgedAlarmAfterRecovery(){request.setAction("CLOSE");request.setExpectedVersion(1L);when(alarm.getVersion()).thenReturn(1L);when(historyMapper.selectOne(any())).thenReturn(null);when(alarm.getStatus()).thenReturn("ACKNOWLEDGED");Equipment e=mock(Equipment.class);when(equipmentMapper.selectById(5L)).thenReturn(e);when(e.getUpDownStatus()).thenReturn("U");when(e.getPrimaryStatus()).thenReturn("IDLE");when(command.nextVersion(1L)).thenReturn(2L);when(alarmMapper.update(any(),any())).thenReturn(1);assertThat(service.executeAction(request).getStatus()).isEqualTo("CLOSED");verify(historyMapper).insert(any(EquipmentAlarmActionHistory.class));}
 @Test void replayReturnsWithoutStateValidation(){EquipmentAlarmActionHistory h=mock(EquipmentAlarmActionHistory.class);when(h.getAlarmId()).thenReturn(10L);when(h.getAction()).thenReturn("ACKNOWLEDGE");when(h.getOperatorId()).thenReturn("ENG-001");when(historyMapper.selectOne(any())).thenReturn(h);when(alarm.getStatus()).thenReturn("ACKNOWLEDGED");when(alarm.getVersion()).thenReturn(1L);assertThat(service.executeAction(request).isIdempotent()).isTrue();verify(alarmMapper,never()).update(any(),any());}
 @Test void sameKeyWithDifferentOperatorIsConflict(){EquipmentAlarmActionHistory h=mock(EquipmentAlarmActionHistory.class);when(h.getAlarmId()).thenReturn(10L);when(h.getAction()).thenReturn("ACKNOWLEDGE");when(h.getOperatorId()).thenReturn("ENG-OTHER");when(historyMapper.selectOne(any())).thenReturn(h);assertThatThrownBy(()->service.executeAction(request)).isInstanceOfSatisfying(AlarmCommandException.class,x->assertThat(x.getErrorCode()).isEqualTo(AlarmCommandErrorCode.IDEMPOTENCY_CONFLICT));}
 @Test void concurrentUpdateDoesNotAppendHistory(){when(historyMapper.selectOne(any())).thenReturn(null);when(alarm.getStatus()).thenReturn("ACTIVE");when(command.nextVersion(0L)).thenReturn(1L);when(alarmMapper.update(any(),any())).thenReturn(0);assertThatThrownBy(()->service.executeAction(request)).isInstanceOf(AlarmCommandException.class);verify(historyMapper,never()).insert(any(EquipmentAlarmActionHistory.class));}
}