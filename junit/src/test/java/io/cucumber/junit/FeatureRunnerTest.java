package io.cucumber.junit;

import io.cucumber.core.io.ClassFinder;
import io.cucumber.core.io.MultiLoader;
import io.cucumber.core.io.ResourceLoader;
import io.cucumber.core.io.ResourceLoaderClassFinder;
import io.cucumber.core.runtime.ConfiguringTypeRegistrySupplier;
import io.cucumber.core.runtime.ObjectFactorySupplier;
import io.cucumber.core.runtime.SingletonObjectFactorySupplier;
import io.cucumber.core.runtime.TimeServiceEventBus;
import io.cucumber.core.eventbus.EventBus;
import io.cucumber.core.runtime.BackendSupplier;
import io.cucumber.core.options.RuntimeOptions;
import io.cucumber.core.runtime.ThreadLocalRunnerSupplier;
import io.cucumber.core.filter.Filters;
import io.cucumber.core.feature.CucumberFeature;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.InitializationError;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;

import static java.util.Collections.singleton;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

public class FeatureRunnerTest {

    private static void assertDescriptionIsPredictable(Description description, Set<Description> descriptions) {
        assertTrue(descriptions.contains(description));
        for (Description each : description.getChildren()) {
            assertDescriptionIsPredictable(each, descriptions);
        }
    }

    private static void assertDescriptionIsUnique(Description description, Set<Description> descriptions) {
        // Note: JUnit uses the the serializable parameter as the unique id when comparing Descriptions
        assertTrue(descriptions.add(description));
        for (Description each : description.getChildren()) {
            assertDescriptionIsUnique(each, descriptions);
        }
    }

    @Test
    public void should_not_create_step_descriptions_by_default() throws Exception {
        CucumberFeature cucumberFeature = TestPickleBuilder.parseFeature("path/test.feature", "" +
            "Feature: feature name\n" +
            "  Background:\n" +
            "    Given background step\n" +
            "  Scenario: A\n" +
            "    Then scenario name\n" +
            "  Scenario: B\n" +
            "    Then scenario name\n" +
            "  Scenario Outline: C\n" +
            "    Then scenario <name>\n" +
            "  Examples:\n" +
            "    | name |\n" +
            "    | C    |\n" +
            "    | D    |\n" +
            "    | E    |\n"

        );

        FeatureRunner runner = createFeatureRunner(cucumberFeature, new JUnitOptions());

        Description feature = runner.getDescription();
        Description scenarioA = feature.getChildren().get(0);
        assertTrue(scenarioA.getChildren().isEmpty());
        Description scenarioB = feature.getChildren().get(1);
        assertTrue(scenarioB.getChildren().isEmpty());
        Description scenarioC0 = feature.getChildren().get(2);
        assertTrue(scenarioC0.getChildren().isEmpty());
        Description scenarioC1 = feature.getChildren().get(3);
        assertTrue(scenarioC1.getChildren().isEmpty());
        Description scenarioC2 = feature.getChildren().get(4);
        assertTrue(scenarioC2.getChildren().isEmpty());
    }

    @Test
    public void should_not_issue_notification_for_steps_by_default_scenario_outline_with_two_examples_table_and_background() throws Throwable {
        CucumberFeature feature = TestPickleBuilder.parseFeature("path/test.feature", "" +
            "Feature: feature name\n" +
            "  Background: background\n" +
            "    Given first step\n" +
            "  Scenario Outline: scenario outline name\n" +
            "    When <x> step\n" +
            "    Then <y> step\n" +
            "    Examples: examples 1 name\n" +
            "      |   x    |   y   |\n" +
            "      | second | third |\n" +
            "      | second | third |\n" +
            "    Examples: examples 2 name\n" +
            "      |   x    |   y   |\n" +
            "      | second | third |\n");

        RunNotifier notifier = runFeatureWithNotifier(feature, new JUnitOptions());

        InOrder order = inOrder(notifier);

        order.verify(notifier).fireTestStarted(argThat(new DescriptionMatcher("scenario outline name(feature name)")));
        order.verify(notifier, times(1)).fireTestAssumptionFailed(argThat(new FailureMatcher("scenario outline name(feature name)")));
        order.verify(notifier).fireTestFinished(argThat(new DescriptionMatcher("scenario outline name(feature name)")));
        order.verify(notifier).fireTestStarted(argThat(new DescriptionMatcher("scenario outline name(feature name)")));
        order.verify(notifier, times(1)).fireTestAssumptionFailed(argThat(new FailureMatcher("scenario outline name(feature name)")));
        order.verify(notifier).fireTestFinished(argThat(new DescriptionMatcher("scenario outline name(feature name)")));
        order.verify(notifier).fireTestStarted(argThat(new DescriptionMatcher("scenario outline name(feature name)")));
        order.verify(notifier, times(1)).fireTestAssumptionFailed(argThat(new FailureMatcher("scenario outline name(feature name)")));
        order.verify(notifier).fireTestFinished(argThat(new DescriptionMatcher("scenario outline name(feature name)")));
    }

    @Test
    public void should_not_issue_notification_for_steps_by_default_two_scenarios_with_background() throws Throwable {
        CucumberFeature feature = TestPickleBuilder.parseFeature("path/test.feature", "" +
            "Feature: feature name\n" +
            "  Background: background\n" +
            "    Given first step\n" +
            "  Scenario: scenario_1 name\n" +
            "    When second step\n" +
            "    Then third step\n" +
            "  Scenario: scenario_2 name\n" +
            "    Then another second step\n");

        RunNotifier notifier = runFeatureWithNotifier(feature, new JUnitOptions());

        InOrder order = inOrder(notifier);

        order.verify(notifier).fireTestStarted(argThat(new DescriptionMatcher("scenario_1 name(feature name)")));
        order.verify(notifier, times(1)).fireTestAssumptionFailed(argThat(new FailureMatcher("scenario_1 name(feature name)")));
        order.verify(notifier).fireTestFinished(argThat(new DescriptionMatcher("scenario_1 name(feature name)")));
        order.verify(notifier).fireTestStarted(argThat(new DescriptionMatcher("scenario_2 name(feature name)")));
        order.verify(notifier, times(1)).fireTestAssumptionFailed(argThat(new FailureMatcher("scenario_2 name(feature name)")));
        order.verify(notifier).fireTestFinished(argThat(new DescriptionMatcher("scenario_2 name(feature name)")));
    }

    private RunNotifier runFeatureWithNotifier(CucumberFeature cucumberFeature, JUnitOptions options) throws InitializationError {
        FeatureRunner runner = createFeatureRunner(cucumberFeature, options);
        RunNotifier notifier = mock(RunNotifier.class);
        runner.run(notifier);
        return notifier;
    }

    private FeatureRunner createFeatureRunner(CucumberFeature cucumberFeature, JUnitOptions junitOption) throws InitializationError {
        ObjectFactorySupplier objectFactory = new SingletonObjectFactorySupplier();
        final RuntimeOptions runtimeOptions = RuntimeOptions.defaultOptions();

        final Clock clockStub = new Clock() {
            @Override
            public Instant instant() {
                return Instant.EPOCH;
            }

            @Override
            public ZoneId getZone() {
                return null;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return null;
            }
        };
        BackendSupplier backendSupplier = () -> singleton(new StubBackendProviderService.StubBackend());

        EventBus bus = new TimeServiceEventBus(clockStub);
        Filters filters = new Filters(runtimeOptions);
        ClassLoader classLoader = ClassLoader.getSystemClassLoader();
        ResourceLoader resourceLoader = new MultiLoader(classLoader);
        ClassFinder classFinder = new ResourceLoaderClassFinder(resourceLoader, classLoader);
        ConfiguringTypeRegistrySupplier typeRegistrySupplier = new ConfiguringTypeRegistrySupplier(classFinder, runtimeOptions);
        ThreadLocalRunnerSupplier runnerSupplier = new ThreadLocalRunnerSupplier(runtimeOptions, bus, backendSupplier, objectFactory, typeRegistrySupplier);
        return new FeatureRunner(cucumberFeature, filters, runnerSupplier, junitOption);
    }

    @Test
    public void should_populate_descriptions_with_stable_unique_ids() throws Exception {
        CucumberFeature cucumberFeature = TestPickleBuilder.parseFeature("path/test.feature", "" +
            "Feature: feature name\n" +
            "  Background:\n" +
            "    Given background step\n" +
            "  Scenario: A\n" +
            "    Then scenario name\n" +
            "  Scenario: B\n" +
            "    Then scenario name\n" +
            "  Scenario Outline: C\n" +
            "    Then scenario <name>\n" +
            "  Examples:\n" +
            "    | name |\n" +
            "    | C    |\n" +
            "    | D    |\n" +
            "    | E    |\n"

        );

        FeatureRunner runner = createFeatureRunner(cucumberFeature, new JUnitOptions());
        FeatureRunner rerunner = createFeatureRunner(cucumberFeature, new JUnitOptions());

        Set<Description> descriptions = new HashSet<>();
        assertDescriptionIsUnique(runner.getDescription(), descriptions);
        assertDescriptionIsPredictable(runner.getDescription(), descriptions);
        assertDescriptionIsPredictable(rerunner.getDescription(), descriptions);

    }

    @Test
    public void step_descriptions_can_be_turned_on() throws Exception {
        CucumberFeature cucumberFeature = TestPickleBuilder.parseFeature("path/test.feature", "" +
            "Feature: feature name\n" +
            "  Background:\n" +
            "    Given background step\n" +
            "  Scenario: A\n" +
            "    Then scenario name\n" +
            "  Scenario: B\n" +
            "    Then scenario name\n" +
            "  Scenario Outline: C\n" +
            "    Then scenario <name>\n" +
            "  Examples:\n" +
            "    | name |\n" +
            "    | C    |\n" +
            "    | D    |\n" +
            "    | E    |\n"

        );

        JUnitOptions junitOption = new JUnitOptionsBuilder().setStepNotifications(true).build();
        FeatureRunner runner = createFeatureRunner(cucumberFeature, junitOption);

        Description feature = runner.getDescription();
        Description scenarioA = feature.getChildren().get(0);
        assertEquals(2, scenarioA.getChildren().size());
        Description scenarioB = feature.getChildren().get(1);
        assertEquals(2, scenarioB.getChildren().size());
        Description scenarioC0 = feature.getChildren().get(2);
        assertEquals(2, scenarioC0.getChildren().size());
        Description scenarioC1 = feature.getChildren().get(3);
        assertEquals(2, scenarioC1.getChildren().size());
        Description scenarioC2 = feature.getChildren().get(4);
        assertEquals(2, scenarioC2.getChildren().size());
    }

    @Test
    public void step_notification_can_be_turned_on_scenario_outline_with_two_examples_table_and_background() throws Throwable {
        CucumberFeature feature = TestPickleBuilder.parseFeature("path/test.feature", "" +
            "Feature: feature name\n" +
            "  Background: background\n" +
            "    Given first step\n" +
            "  Scenario Outline: scenario outline name\n" +
            "    When <x> step\n" +
            "    Then <y> step\n" +
            "    Examples: examples 1 name\n" +
            "      |   x    |   y   |\n" +
            "      | second | third |\n" +
            "      | second | third |\n" +
            "    Examples: examples 2 name\n" +
            "      |   x    |   y   |\n" +
            "      | second | third |\n");

        JUnitOptions junitOption = new JUnitOptionsBuilder().setStepNotifications(true).build();
        RunNotifier notifier = runFeatureWithNotifier(feature, junitOption);

        InOrder order = inOrder(notifier);

        order.verify(notifier).fireTestStarted(argThat(new DescriptionMatcher("scenario outline name")));
        order.verify(notifier).fireTestStarted(argThat(new DescriptionMatcher("first step(scenario outline name)")));
        order.verify(notifier).fireTestAssumptionFailed(argThat(new FailureMatcher("first step(scenario outline name)")));
        order.verify(notifier).fireTestFinished(argThat(new DescriptionMatcher("first step(scenario outline name)")));
        order.verify(notifier).fireTestStarted(argThat(new DescriptionMatcher("second step(scenario outline name)")));
        order.verify(notifier).fireTestAssumptionFailed(argThat(new FailureMatcher("second step(scenario outline name)")));
        order.verify(notifier).fireTestFinished(argThat(new DescriptionMatcher("second step(scenario outline name)")));
        order.verify(notifier).fireTestStarted(argThat(new DescriptionMatcher("third step(scenario outline name)")));
        order.verify(notifier).fireTestAssumptionFailed(argThat(new FailureMatcher("third step(scenario outline name)")));
        order.verify(notifier).fireTestFinished(argThat(new DescriptionMatcher("third step(scenario outline name)")));
        order.verify(notifier).fireTestFinished(argThat(new DescriptionMatcher("scenario outline name")));
        order.verify(notifier).fireTestStarted(argThat(new DescriptionMatcher("scenario outline name")));
        order.verify(notifier).fireTestStarted(argThat(new DescriptionMatcher("first step(scenario outline name)")));
        order.verify(notifier).fireTestAssumptionFailed(argThat(new FailureMatcher("first step(scenario outline name)")));
        order.verify(notifier).fireTestFinished(argThat(new DescriptionMatcher("first step(scenario outline name)")));
        order.verify(notifier).fireTestStarted(argThat(new DescriptionMatcher("second step(scenario outline name)")));
        order.verify(notifier).fireTestAssumptionFailed(argThat(new FailureMatcher("second step(scenario outline name)")));
        order.verify(notifier).fireTestFinished(argThat(new DescriptionMatcher("second step(scenario outline name)")));
        order.verify(notifier).fireTestStarted(argThat(new DescriptionMatcher("third step(scenario outline name)")));
        order.verify(notifier).fireTestAssumptionFailed(argThat(new FailureMatcher("third step(scenario outline name)")));
        order.verify(notifier).fireTestFinished(argThat(new DescriptionMatcher("third step(scenario outline name)")));
        order.verify(notifier).fireTestFinished(argThat(new DescriptionMatcher("scenario outline name")));
        order.verify(notifier).fireTestStarted(argThat(new DescriptionMatcher("scenario outline name")));
        order.verify(notifier).fireTestStarted(argThat(new DescriptionMatcher("first step(scenario outline name)")));
        order.verify(notifier).fireTestAssumptionFailed(argThat(new FailureMatcher("first step(scenario outline name)")));
        order.verify(notifier).fireTestFinished(argThat(new DescriptionMatcher("first step(scenario outline name)")));
        order.verify(notifier).fireTestStarted(argThat(new DescriptionMatcher("second step(scenario outline name)")));
        order.verify(notifier).fireTestAssumptionFailed(argThat(new FailureMatcher("second step(scenario outline name)")));
        order.verify(notifier).fireTestFinished(argThat(new DescriptionMatcher("second step(scenario outline name)")));
        order.verify(notifier).fireTestStarted(argThat(new DescriptionMatcher("third step(scenario outline name)")));
        order.verify(notifier).fireTestAssumptionFailed(argThat(new FailureMatcher("third step(scenario outline name)")));
        order.verify(notifier).fireTestFinished(argThat(new DescriptionMatcher("third step(scenario outline name)")));
        order.verify(notifier).fireTestFinished(argThat(new DescriptionMatcher("scenario outline name")));
    }

    @Test
    public void step_notification_can_be_turned_on_two_scenarios_with_background() throws Throwable {
        CucumberFeature feature = TestPickleBuilder.parseFeature("path/test.feature", "" +
            "Feature: feature name\n" +
            "  Background: background\n" +
            "    Given first step\n" +
            "  Scenario: scenario_1 name\n" +
            "    When second step\n" +
            "    Then third step\n" +
            "  Scenario: scenario_2 name\n" +
            "    Then another second step\n");

        JUnitOptions junitOption = new JUnitOptionsBuilder().setStepNotifications(true).build();
        RunNotifier notifier = runFeatureWithNotifier(feature, junitOption);

        InOrder order = inOrder(notifier);

        order.verify(notifier).fireTestStarted(argThat(new DescriptionMatcher("scenario_1 name")));
        order.verify(notifier).fireTestStarted(argThat(new DescriptionMatcher("first step(scenario_1 name)")));
        order.verify(notifier).fireTestAssumptionFailed(argThat(new FailureMatcher("first step(scenario_1 name)")));
        order.verify(notifier).fireTestFinished(argThat(new DescriptionMatcher("first step(scenario_1 name)")));
        order.verify(notifier).fireTestStarted(argThat(new DescriptionMatcher("second step(scenario_1 name)")));
        order.verify(notifier).fireTestAssumptionFailed(argThat(new FailureMatcher("second step(scenario_1 name)")));
        order.verify(notifier).fireTestFinished(argThat(new DescriptionMatcher("second step(scenario_1 name)")));
        order.verify(notifier).fireTestStarted(argThat(new DescriptionMatcher("third step(scenario_1 name)")));
        order.verify(notifier).fireTestAssumptionFailed(argThat(new FailureMatcher("third step(scenario_1 name)")));
        order.verify(notifier).fireTestFinished(argThat(new DescriptionMatcher("third step(scenario_1 name)")));
        order.verify(notifier).fireTestFinished(argThat(new DescriptionMatcher("scenario_1 name")));
        order.verify(notifier).fireTestStarted(argThat(new DescriptionMatcher("scenario_2 name")));
        order.verify(notifier).fireTestStarted(argThat(new DescriptionMatcher("first step(scenario_2 name)")));
        order.verify(notifier).fireTestAssumptionFailed(argThat(new FailureMatcher("first step(scenario_2 name)")));
        order.verify(notifier).fireTestFinished(argThat(new DescriptionMatcher("first step(scenario_2 name)")));
        order.verify(notifier).fireTestStarted(argThat(new DescriptionMatcher("another second step(scenario_2 name)")));
        order.verify(notifier).fireTestAssumptionFailed(argThat(new FailureMatcher("another second step(scenario_2 name)")));
        order.verify(notifier).fireTestFinished(argThat(new DescriptionMatcher("another second step(scenario_2 name)")));
        order.verify(notifier).fireTestFinished(argThat(new DescriptionMatcher("scenario_2 name")));
    }

}
