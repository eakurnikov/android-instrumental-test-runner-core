package com.github.grishberg.tests;

import com.github.grishberg.tests.commands.DeviceCommandResult;
import com.github.grishberg.tests.commands.DeviceRunnerCommand;
import com.github.grishberg.tests.commands.DeviceRunnerCommandProvider;
import com.github.grishberg.tests.commands.ExecuteCommandException;
import com.github.grishberg.tests.common.RunnerLogger;
import com.github.grishberg.tests.planner.InstrumentalTestPlanProvider;

import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Executes commands for online devices.
 */
class DeviceCommandsRunner {
    private static final String TAG = "DCR";
    private final InstrumentalTestPlanProvider testPlanProvider;
    private final DeviceRunnerCommandProvider commandProvider;
    private Environment environment;
    private final RunnerLogger logger;
    private boolean hasFailedTests;
    private Throwable commandException;

    DeviceCommandsRunner(InstrumentalTestPlanProvider testPlanProvider,
                         DeviceRunnerCommandProvider commandProvider,
                         Environment directoriesProvider,
                         RunnerLogger logger) {
        this.testPlanProvider = testPlanProvider;
        this.commandProvider = commandProvider;
        this.environment = directoriesProvider;
        this.logger = logger;
    }

    boolean runCommands(ConnectedDeviceWrapper[] devices) throws InterruptedException,
            ExecuteCommandException {
        final CountDownLatch deviceCounter = new CountDownLatch(devices.length);

        for (ConnectedDeviceWrapper device : devices) {
            new Thread(() -> {
                try {
                    List<DeviceRunnerCommand> commands = commandProvider.provideCommandsForDevice(device,
                            testPlanProvider, environment);
                    for (DeviceRunnerCommand command : commands) {
                        logger.d(TAG, "Before executing device = {} command = {}",
                                device, command.toString());
                        DeviceCommandResult result = command.execute(device);
                        logger.d(TAG, "After executing device = {} command = {}",
                                device, command.toString());
                        if (result.isFailed()) {
                            hasFailedTests = true;
                        }
                    }
                } catch (Exception e) {
                    logger.e(TAG, "Execute command exception:", e);
                    commandException = e;
                } finally {
                    deviceCounter.countDown();
                }
            }).start();
        }
        deviceCounter.await();
        if (commandException != null) {
            logger.e(TAG, "Found exception while executing commands.");
            if (commandException instanceof ExecuteCommandException) {
                throw (ExecuteCommandException) commandException;
            }
            throw new ExecuteCommandException(commandException);
        }
        return !hasFailedTests;
    }
}
