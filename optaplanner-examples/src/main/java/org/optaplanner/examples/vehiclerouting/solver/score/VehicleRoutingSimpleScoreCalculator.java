/*
 * Copyright 2013 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.optaplanner.examples.vehiclerouting.solver.score;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;
import org.optaplanner.core.impl.score.director.simple.SimpleScoreCalculator;
import org.optaplanner.examples.vehiclerouting.domain.Customer;
import org.optaplanner.examples.vehiclerouting.domain.Vehicle;
import org.optaplanner.examples.vehiclerouting.domain.VehicleRoutingSolution;
import org.optaplanner.examples.vehiclerouting.domain.Standstill;
import org.optaplanner.examples.vehiclerouting.domain.timewindowed.TimeWindowedCustomer;
import org.optaplanner.examples.vehiclerouting.domain.timewindowed.TimeWindowedVehicleRoutingSolution;

public class VehicleRoutingSimpleScoreCalculator implements SimpleScoreCalculator<VehicleRoutingSolution> {

    public HardSoftScore calculateScore(VehicleRoutingSolution schedule) {
        boolean timeWindowed = schedule instanceof TimeWindowedVehicleRoutingSolution;
        List<Customer> customerList = schedule.getCustomerList();
        List<Vehicle> vehicleList = schedule.getVehicleList();
        Map<Vehicle, Integer> vehicleDemandMap = new HashMap<Vehicle, Integer>(vehicleList.size());
        for (Vehicle vehicle : vehicleList) {
            vehicleDemandMap.put(vehicle, 0);
        }
        int hardScore = 0;
        int softScore = 0;
        for (Customer customer : customerList) {
            Standstill previousStandstill = customer.getPreviousStandstill();
            if (previousStandstill != null) {
                Vehicle vehicle = customer.getVehicle();
                vehicleDemandMap.put(vehicle, vehicleDemandMap.get(vehicle) + customer.getDemand());
                // Score constraint distanceToPreviousStandstill
                softScore -= customer.getMilliDistanceToPreviousStandstill();
                if (customer.getNextCustomer() == null) {
                    // Score constraint distanceFromLastCustomerToDepot
                    softScore -= vehicle.getLocation().getMilliDistance(customer.getLocation());
                }
                if (timeWindowed) {
                    TimeWindowedCustomer timeWindowedCustomer = (TimeWindowedCustomer) customer;
                    int milliReadyTime = timeWindowedCustomer.getMilliReadyTime();
                    int milliDueTime = timeWindowedCustomer.getMilliDueTime();
                    Integer milliArrivalTime = timeWindowedCustomer.getMilliArrivalTime();
                    if (milliDueTime < milliArrivalTime) {
                        // Score constraint arrivalAfterDueTime
                        hardScore -= (milliArrivalTime - milliDueTime);
                    }
                    if (milliArrivalTime < milliReadyTime) {
                        // Score constraint arrivalBeforeReadyTime
                        // Many external benchmark records tend to ignore this constraint.
                        // That heavily affects the attainable score.
                        softScore -= (milliReadyTime - milliArrivalTime);
                    }
                }
            }
        }
        for (Map.Entry<Vehicle, Integer> entry : vehicleDemandMap.entrySet()) {
            int capacity = entry.getKey().getCapacity();
            int demand = entry.getValue();
            if (demand > capacity) {
                // Score constraint vehicleCapacity
                hardScore -= (demand - capacity);
            }
        }
        return HardSoftScore.valueOf(hardScore, softScore);
    }

}
