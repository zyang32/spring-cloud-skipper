/*
 * Copyright 2017-2018 the original author or authors.
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
package org.springframework.cloud.skipper.server.statemachine;

import org.springframework.cloud.skipper.domain.RollbackRequest;
import org.springframework.cloud.skipper.server.deployer.ReleaseAnalysisReport;
import org.springframework.cloud.skipper.server.deployer.strategies.UpgradeStrategy;
import org.springframework.cloud.skipper.server.deployer.strategies.UpgradeStrategyFactory;
import org.springframework.cloud.skipper.server.service.ReleaseReportService;
import org.springframework.cloud.skipper.server.statemachine.SkipperStateMachineService.SkipperEventHeaders;
import org.springframework.cloud.skipper.server.statemachine.SkipperStateMachineService.SkipperEvents;
import org.springframework.cloud.skipper.server.statemachine.SkipperStateMachineService.SkipperStates;
import org.springframework.cloud.skipper.server.statemachine.SkipperStateMachineService.SkipperVariables;
import org.springframework.cloud.skipper.server.util.ManifestUtils;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;

/**
 * StateMachine {@link Action}
 *
 * @author Janne Valkealahti
 *
 */
public class UpgradeCancelAction extends AbstractUpgradeStartAction {
	
	private final UpgradeStrategyFactory upgradeStrategyFactory;

	/**
	 * Instantiates a new upgrade cancel action.
	 *
	 * @param releaseReportService the release report service
	 * @param upgradeStrategyFactory the upgrade strategy factory
	 */
	public UpgradeCancelAction(ReleaseReportService releaseReportService, UpgradeStrategyFactory upgradeStrategyFactory) {
		super(releaseReportService);
		this.upgradeStrategyFactory = upgradeStrategyFactory;
	}

	@Override
	protected void executeInternal(StateContext<SkipperStates, SkipperEvents> context) {
		super.executeInternal(context);
		
		Long upgradeTimeout = context.getExtendedState().get(SkipperEventHeaders.UPGRADE_TIMEOUT, Long.class);
		Long cutOffTime = context.getExtendedState().get(SkipperVariables.UPGRADE_CUTOFF_TIME, Long.class);
		SkipperEvents event = context.getEvent();
		if (event == SkipperEvents.UPGRADE_CANCEL && cutOffTime != null) {
			// looks like we're manually cancelling, as we have UPGRADE_CANCEL in context,
			// calculate new timeout for reporting so that we can give number
			// how long user waited before cancelling.
			upgradeTimeout = System.currentTimeMillis() - (cutOffTime - upgradeTimeout);
		}
		else {
			// else assume timeout upgrade is using
			upgradeTimeout = context.getExtendedState().get(SkipperEventHeaders.UPGRADE_TIMEOUT, Long.class);
		}
		
		ReleaseAnalysisReport releaseAnalysisReport = getReleaseAnalysisReport(context);
		
		// check if we're doing rollback and pass flag to strategy
		RollbackRequest rollbackRequest = context.getExtendedState().get(SkipperEventHeaders.ROLLBACK_REQUEST,
				RollbackRequest.class);
		// TODO: should check both releases
		String kind = ManifestUtils.resolveKind(releaseAnalysisReport.getExistingRelease().getManifest().getData());
		UpgradeStrategy upgradeStrategy = this.upgradeStrategyFactory.getUpgradeStrategy(kind);		
		upgradeStrategy.cancel(releaseAnalysisReport.getExistingRelease(), releaseAnalysisReport.getReplacingRelease(),
				releaseAnalysisReport, upgradeTimeout, event == SkipperEvents.UPGRADE_CANCEL, rollbackRequest != null);
	}
}
