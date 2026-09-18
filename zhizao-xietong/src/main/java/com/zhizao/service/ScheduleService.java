package com.zhizao.service;

import com.zhizao.entity.Plan;
import com.zhizao.enums.Priority;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 排产：按生产优先级降序，同级按计划开始日期升序。
 */
@Service
public class ScheduleService {

    public List<Plan> sortByPriority(List<Plan> plans) {
        return plans.stream()
                .sorted(Comparator
                        .comparing((Plan p) -> priorityWeight(p.getPriority())).reversed()
                        .thenComparing(Plan::getPlanStart))
                .collect(Collectors.toList());
    }

    private int priorityWeight(String priority) {
        try {
            return Priority.valueOf(priority).getWeight();
        } catch (Exception e) {
            return 1;
        }
    }
}
