/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jidefx.scene.control.field.popup;

import com.sun.javafx.css.converters.BooleanConverter;
import com.sun.javafx.scene.control.skin.resources.ControlResources;
import com.sun.javafx.scene.traversal.Direction;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableBooleanProperty;
import javafx.css.StyleableProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.util.Callback;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.chrono.ChronoLocalDate;
import java.time.chrono.Chronology;
import java.time.chrono.IsoChronology;
import java.time.format.DateTimeFormatter;
import java.time.format.DecimalStyle;
import java.time.temporal.ChronoUnit;
import java.time.temporal.ValueRange;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static com.sun.javafx.PlatformUtil.isMac;
import static java.time.temporal.ChronoField.DAY_OF_WEEK;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoUnit.*;

/**
 * The full content for a date picker or a date combobox. It was copied from AbstractDatePopupContent.java (jdk8 ea build 96)
 * and modified to allow the data type to be LocalDate, Date or Calendar as long as it can represent a date. Internally
 * it uses the LocalDate to draw the day grids. Two methods - {@link #toLocalDate(Object)} and {@link
 * #fromLocalDate(java.time.LocalDate)} can be implemented to provide conversions between the LocalDate and the actual data type.
 */

/**
 * The full content for the DatePicker popup. This class could probably be used more or less as-is with an embeddable
 * type of date picker that doesn't use a popup.
 */
abstract public class AbstractDatePopupContent<T> extends VBox implements PopupContent<T> {
    protected AbstractDatePopupContent<T> datePicker;
    private Button backMonthButton;
    private Button forwardMonthButton;
    private Button backYearButton;
    private Button forwardYearButton;
    private Label monthLabel;
    private Label yearLabel;
    protected GridPane gridPane;

    private int daysPerWeek;
    private List<DateCell> dayNameCells = new ArrayList<DateCell>();
    private List<DateCell> weekNumberCells = new ArrayList<DateCell>();
    protected List<DateCell> dayCells = new ArrayList<DateCell>();
    private LocalDate[] dayCellDates;
    private DateCell lastFocusedDayCell = null;

    final DateTimeFormatter monthFormatter =
            DateTimeFormatter.ofPattern("MMMM");

    final DateTimeFormatter monthFormatterSO =
            DateTimeFormatter.ofPattern("LLLL"); // Standalone month name

    final DateTimeFormatter yearFormatter =
            DateTimeFormatter.ofPattern("y");

    final DateTimeFormatter yearWithEraFormatter =
            DateTimeFormatter.ofPattern("GGGGy"); // For Japanese. What to use for others??

    final DateTimeFormatter weekNumberFormatter =
            DateTimeFormatter.ofPattern("w");

    final DateTimeFormatter weekDayNameFormatter =
            DateTimeFormatter.ofPattern("ccc"); // Standalone day name

    final DateTimeFormatter dayCellFormatter =
            DateTimeFormatter.ofPattern("d");

    final ContextMenu contextMenu = new ContextMenu();

    static String getString(String key) {
        return ControlResources.getString("DatePicker." + key);
    }

    public AbstractDatePopupContent() {
        this.datePicker = this;

        getStyleClass().add("date-picker-popup");

        daysPerWeek = getDaysPerWeek();

        contextMenu.getItems().addAll(
                new MenuItem(getString("contextMenu.showToday")) {{
                    setOnAction(new EventHandler<ActionEvent>() {
                        @Override
                        public void handle(ActionEvent t) {
                            displayedYearMonth.set(YearMonth.now());
                        }
                    });
                }},
                new SeparatorMenuItem(),
                new CheckMenuItem(getString("contextMenu.showWeekNumbers")) {{
                    selectedProperty().bindBidirectional(datePicker.showWeekNumbersProperty());
                }}
        );

        setOnContextMenuRequested(new EventHandler<ContextMenuEvent>() {
            @Override
            public void handle(ContextMenuEvent me) {
                contextMenu.show(AbstractDatePopupContent.this, me.getScreenX(), me.getScreenY());
                me.consume();
            }
        });

        {
            T date = datePicker.getValue();
            displayedYearMonth.set((date != null) ? YearMonth.from(toLocalDate(date)) : YearMonth.now());
        }

        displayedYearMonth.addListener(new ChangeListener<YearMonth>() {
            @Override
            public void changed(ObservableValue<? extends YearMonth> observable,
                                YearMonth oldValue, YearMonth newValue) {
                updateValues();
            }
        });


        getChildren().add(createMonthYearPane());

        gridPane = new GridPane() {
            @Override
            protected double computePrefWidth(double height) {
                final double width = super.computePrefWidth(height);

                // RT-30903: Make sure width snaps to pixel when divided by
                // number of columns. GridPane doesn't do this with percentage
                // width constraints. See GridPane.adjustColumnWidths().
                final int nCols = daysPerWeek + (datePicker.isShowWeekNumbers() ? 1 : 0);
                final double snaphgap = snapSpace(getHgap());
                final double left = snapSpace(getInsets().getLeft());
                final double right = snapSpace(getInsets().getRight());
                final double hgaps = snaphgap * (nCols - 1);
                final double contentWidth = width - left - right - hgaps;
                return ((snapSize(contentWidth / nCols)) * nCols) + left + right + hgaps;
            }
        };
        gridPane.setFocusTraversable(true);
        gridPane.getStyleClass().add("calendar-grid");
        gridPane.setVgap(-1);
        gridPane.setHgap(-1);

        gridPane.focusedProperty().addListener(new ChangeListener<Boolean>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> ov, Boolean t, Boolean hasFocus) {
                if (hasFocus) {
                    if (lastFocusedDayCell != null) {
                        Platform.runLater(new Runnable() {
                            @Override
                            public void run() {
                                lastFocusedDayCell.requestFocus();
                            }
                        });
                    }
                    else {
                        clearFocus();
                    }
                }
            }
        });

        // get the weekday labels starting with the weekday that is the
        // first-day-of-the-week according to the locale in the
        // displayed LocalDate
        for (int i = 0; i < daysPerWeek; i++) {
            DateCell cell = new DateCell();
            cell.getStyleClass().add("day-name-cell");
            dayNameCells.add(cell);
        }

        // Week number column
        for (int i = 0; i < 6; i++) {
            DateCell cell = new DateCell();
            cell.getStyleClass().add("week-number-cell");
            weekNumberCells.add(cell);
        }

        createDayCells();
        updateGrid();
        getChildren().add(gridPane);

        refresh();

        // RT-30511: This enables traversal (not sure why Scene doesn't handle this),
        // plus it prevents key events from reaching the popup's owner.
        addEventHandler(KeyEvent.ANY, new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent e) {
                Node node = getScene().getFocusOwner();
                if (node instanceof DateCell) {
                    lastFocusedDayCell = (DateCell) node;
                }

                if (e.getEventType() == KeyEvent.KEY_PRESSED) {
                    switch (e.getCode()) {
                        case TAB:
                            node.impl_traverse(e.isShiftDown() ? Direction.PREVIOUS : Direction.NEXT);
                            e.consume();
                            break;

                        case UP:
                            if (!e.isAltDown()) {
                                node.impl_traverse(Direction.UP);
                                e.consume();
                            }
                            break;

                        case DOWN:
                            if (!e.isAltDown()) {
                                node.impl_traverse(Direction.DOWN);
                                e.consume();
                            }
                            break;

                        case LEFT:
                            node.impl_traverse(Direction.LEFT);
                            e.consume();
                            break;

                        case RIGHT:
                            node.impl_traverse(Direction.RIGHT);
                            e.consume();
                            break;

                        case PAGE_UP:
                            if ((isMac() && e.isMetaDown()) || (!isMac() && e.isControlDown())) {
                                if (!backYearButton.isDisabled()) {
                                    forward(-1, YEARS);
                                }
                            }
                            else {
                                if (!backMonthButton.isDisabled()) {
                                    forward(-1, MONTHS);
                                }
                            }
                            e.consume();
                            break;

                        case PAGE_DOWN:
                            if ((isMac() && e.isMetaDown()) || (!isMac() && e.isControlDown())) {
                                if (!forwardYearButton.isDisabled()) {
                                    forward(1, YEARS);
                                }
                            }
                            else {
                                if (!forwardMonthButton.isDisabled()) {
                                    forward(1, MONTHS);
                                }
                            }
                            e.consume();
                            break;
                    }

                    node = getScene().getFocusOwner();
                    if (node instanceof DateCell) {
                        lastFocusedDayCell = (DateCell) node;
                    }
                }

                // Consume all key events except those that control
                // showing the popup.
                switch (e.getCode()) {
                    case ESCAPE:
                    case F4:
                    case F10:
                    case UP:
                    case DOWN:
                        break;

                    default:
                        e.consume();
                }
            }
        });
    }

    private ObjectProperty<YearMonth> displayedYearMonth =
            new SimpleObjectProperty<YearMonth>(this, "displayedYearMonth");

    ObjectProperty<YearMonth> displayedYearMonthProperty() {
        return displayedYearMonth;
    }


    protected BorderPane createMonthYearPane() {
        BorderPane monthYearPane = new BorderPane();
        monthYearPane.getStyleClass().add("month-year-pane");

        // Month spinner

        HBox monthSpinner = new HBox();
        monthSpinner.getStyleClass().add("spinner");

        backMonthButton = new Button();
        backMonthButton.getStyleClass().add("left-button");

        forwardMonthButton = new Button();
        forwardMonthButton.getStyleClass().add("right-button");

        StackPane leftMonthArrow = new StackPane();
        leftMonthArrow.getStyleClass().add("left-arrow");
        leftMonthArrow.setMaxSize(USE_PREF_SIZE, USE_PREF_SIZE);
        backMonthButton.setGraphic(leftMonthArrow);

        StackPane rightMonthArrow = new StackPane();
        rightMonthArrow.getStyleClass().add("right-arrow");
        rightMonthArrow.setMaxSize(USE_PREF_SIZE, USE_PREF_SIZE);
        forwardMonthButton.setGraphic(rightMonthArrow);


        backMonthButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent t) {
                forward(-1, MONTHS);
            }
        });

        monthLabel = new Label();
        monthLabel.getStyleClass().add("spinner-label");

        forwardMonthButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent t) {
                forward(1, MONTHS);
            }
        });

        monthSpinner.getChildren().addAll(backMonthButton, monthLabel, forwardMonthButton);
        monthYearPane.setLeft(monthSpinner);

        // Year spinner

        HBox yearSpinner = new HBox();
        yearSpinner.getStyleClass().add("spinner");

        backYearButton = new Button();
        backYearButton.getStyleClass().add("left-button");

        forwardYearButton = new Button();
        forwardYearButton.getStyleClass().add("right-button");

        StackPane leftYearArrow = new StackPane();
        leftYearArrow.getStyleClass().add("left-arrow");
        leftYearArrow.setMaxSize(USE_PREF_SIZE, USE_PREF_SIZE);
        backYearButton.setGraphic(leftYearArrow);

        StackPane rightYearArrow = new StackPane();
        rightYearArrow.getStyleClass().add("right-arrow");
        rightYearArrow.setMaxSize(USE_PREF_SIZE, USE_PREF_SIZE);
        forwardYearButton.setGraphic(rightYearArrow);


        backYearButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent t) {
                forward(-1, YEARS);
            }
        });

        yearLabel = new Label();
        yearLabel.getStyleClass().add("spinner-label");

        forwardYearButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent t) {
                forward(1, YEARS);
            }
        });


        yearSpinner.getChildren().addAll(backYearButton, yearLabel, forwardYearButton);
        yearSpinner.setFillHeight(false);
        monthYearPane.setRight(yearSpinner);

        return monthYearPane;
    }

    private void refresh() {
        updateMonthLabelWidth();
        updateDayNameCells();
        updateValues();
    }

    void updateValues() {
        // Note: Preserve this order, as DatePickerHijrahContent needs
        // updateDayCells before updateMonthYearPane().
        updateWeeknumberDateCells();
        updateDayCells();
        updateMonthYearPane();
    }

    void updateGrid() {
        gridPane.getColumnConstraints().clear();
        gridPane.getChildren().clear();

        int nCols = daysPerWeek + (datePicker.isShowWeekNumbers() ? 1 : 0);

        ColumnConstraints columnConstraints = new ColumnConstraints();
        columnConstraints.setPercentWidth(100); // Treated as weight
        for (int i = 0; i < nCols; i++) {
            gridPane.getColumnConstraints().add(columnConstraints);
        }

        for (int i = 0; i < daysPerWeek; i++) {
            gridPane.add(dayNameCells.get(i), i + nCols - daysPerWeek, 1);  // col, row
        }

        // Week number column
        if (datePicker.isShowWeekNumbers()) {
            for (int i = 0; i < 6; i++) {
                gridPane.add(weekNumberCells.get(i), 0, i + 2);  // col, row
            }
        }

        // setup: 6 rows of daysPerWeek (which is the maximum number of cells required in the worst case layout)
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < daysPerWeek; col++) {
                gridPane.add(dayCells.get(row * daysPerWeek + col), col + nCols - daysPerWeek, row + 2);
            }
        }
    }

    void updateDayNameCells() {
        // first day of week, 1 = monday, 7 = sunday
        int firstDayOfWeek = WeekFields.of(getLocale()).getFirstDayOfWeek().getValue();

        // july 13th 2009 is a Monday, so a firstDayOfWeek=1 must come out of the 13th
        LocalDate date = LocalDate.of(2009, 7, 12 + firstDayOfWeek);
        for (int i = 0; i < daysPerWeek; i++) {
            String name = weekDayNameFormatter.withLocale(getLocale()).format(date.plus(i, DAYS));
            dayNameCells.get(i).setText(titleCaseWord(name));
        }
    }

    void updateWeeknumberDateCells() {
        if (datePicker.isShowWeekNumbers()) {
            final Locale locale = getLocale();
            final int maxWeeksPerMonth = 6; // TODO: Get this from chronology?

            LocalDate firstOfMonth = displayedYearMonth.get().atDay(1);
            for (int i = 0; i < maxWeeksPerMonth; i++) {
                LocalDate date = firstOfMonth.plus(i, WEEKS);
                // Use a formatter to ensure correct localization,
                // such as when Thai numerals are required.
                String cellText =
                        weekNumberFormatter.withLocale(locale)
                                .withDecimalStyle(DecimalStyle.of(locale))
                                .format(date);
                weekNumberCells.get(i).setText(cellText);
            }
        }
    }

    void updateDayCells() {
        Locale locale = getLocale();
        Chronology chrono = getPrimaryChronology();
        int firstOfMonthIdx = determineFirstOfMonthDayOfWeek();
        YearMonth curMonth = displayedYearMonth.get();
        YearMonth prevMonth = curMonth.minusMonths(1);
        YearMonth nextMonth = curMonth.plusMonths(1);
        int daysInCurMonth = determineDaysInMonth(curMonth);
        int daysInPrevMonth = determineDaysInMonth(prevMonth);
        int daysInNextMonth = determineDaysInMonth(nextMonth);

        for (int i = 0; i < 6 * daysPerWeek; i++) {
            DateCell dayCell = dayCells.get(i);
            dayCell.getStyleClass().setAll("cell", "day-cell");
            dayCell.setDisable(false);
            dayCell.setStyle(null);
            dayCell.setGraphic(null);
            dayCell.setTooltip(null);

            try {
                YearMonth month = curMonth;
                int day = i - firstOfMonthIdx + 1;
                //int index = firstOfMonthIdx + i - 1;
                if (i < firstOfMonthIdx) {
                    month = prevMonth;
                    day = i + daysInPrevMonth - firstOfMonthIdx + 1;
                    dayCell.getStyleClass().add("previous-month");
                }
                else if (i >= firstOfMonthIdx + daysInCurMonth) {
                    month = nextMonth;
                    day = i - daysInCurMonth - firstOfMonthIdx + 1;
                    dayCell.getStyleClass().add("next-month");
                }
                LocalDate date = month.atDay(day);
                dayCellDates[i] = date;
                ChronoLocalDate cDate = chrono.date(date);

                dayCell.setDisable(false);

                if (isToday(date)) {
                    dayCell.getStyleClass().add("today");
                }

                if (date.equals(datePicker.getValue())) {
                    dayCell.getStyleClass().add("selected");
                }

                String cellText =
                        dayCellFormatter.withLocale(locale)
                                .withChronology(chrono)
                                .withDecimalStyle(DecimalStyle.of(locale))
                                .format(cDate);
                dayCell.setText(cellText);

                dayCell.updateItem(date, false);
            }
            catch (DateTimeException ex) {
                // Date is out of range.
                // System.err.println(dayCellDate(dayCell) + " " + ex);
                dayCell.setText(" ");
                dayCell.setDisable(true);
            }
        }
    }

    private int getDaysPerWeek() {
        ValueRange range = getPrimaryChronology().range(DAY_OF_WEEK);
        return (int) (range.getMaximum() - range.getMinimum() + 1);
    }

    private int getMonthsPerYear() {
        ValueRange range = getPrimaryChronology().range(MONTH_OF_YEAR);
        return (int) (range.getMaximum() - range.getMinimum() + 1);
    }

    private void updateMonthLabelWidth() {
        if (monthLabel != null) {
            int monthsPerYear = getMonthsPerYear();
            double width = 0;
            for (int i = 0; i < monthsPerYear; i++) {
                YearMonth yearMonth = displayedYearMonth.get().withMonth(i + 1);
                String name = monthFormatterSO.withLocale(getLocale()).format(yearMonth);
                if (Character.isDigit(name.charAt(0))) {
                    // Fallback. The standalone format returned a number, so use standard format instead.
                    name = monthFormatter.withLocale(getLocale()).format(yearMonth);
                }
                width = Math.max(width, computeTextWidth(monthLabel.getFont(), name, 0));
            }
            monthLabel.setMinWidth(width);
        }
    }

    protected void updateMonthYearPane() {
        YearMonth yearMonth = displayedYearMonth.get();
        String str = formatMonth(yearMonth);
        monthLabel.setText(str);

        str = formatYear(yearMonth);
        yearLabel.setText(str);
        double width = computeTextWidth(yearLabel.getFont(), str, 0);
        if (width > yearLabel.getMinWidth()) {
            yearLabel.setMinWidth(width);
        }

        Chronology chrono = datePicker.getChronology();
        LocalDate firstDayOfMonth = yearMonth.atDay(1);
        backMonthButton.setDisable(!isValidDate(chrono, firstDayOfMonth.minusDays(1)));
        forwardMonthButton.setDisable(!isValidDate(chrono, firstDayOfMonth.plusMonths(1)));
        backYearButton.setDisable(!isValidDate(chrono, firstDayOfMonth.minusYears(1)));
        forwardYearButton.setDisable(!isValidDate(chrono, firstDayOfMonth.plusYears(1)));
    }

    private String formatMonth(YearMonth yearMonth) {
        Locale locale = getLocale();
        Chronology chrono = getPrimaryChronology();
        try {
            ChronoLocalDate cDate = chrono.date(yearMonth.atDay(1));

            String str = monthFormatterSO.withLocale(getLocale())
                    .withChronology(chrono)
                    .format(cDate);
            if (Character.isDigit(str.charAt(0))) {
                // Fallback. The standalone format returned a number, so use standard format instead.
                str = monthFormatter.withLocale(getLocale())
                        .withChronology(chrono)
                        .format(cDate);
            }
            return titleCaseWord(str);
        }
        catch (DateTimeException ex) {
            // Date is out of range.
            return "";
        }
    }

    private String formatYear(YearMonth yearMonth) {
        Locale locale = getLocale();
        Chronology chrono = getPrimaryChronology();
        try {
            DateTimeFormatter formatter = yearFormatter;
            ChronoLocalDate cDate = chrono.date(yearMonth.atDay(1));
            int era = cDate.getEra().getValue();
            int nEras = chrono.eras().size();

            /*if (cDate.get(YEAR) < 0) {
                formatter = yearForNegYearFormatter;
            } else */
            if ((nEras == 2 && era == 0) || nEras > 2) {
                formatter = yearWithEraFormatter;
            }

            // Fixme: Format Japanese era names with Japanese text.
            String str = formatter.withLocale(getLocale())
                    .withChronology(chrono)
                    .withDecimalStyle(DecimalStyle.of(getLocale()))
                    .format(cDate);

            return str;
        }
        catch (DateTimeException ex) {
            // Date is out of range.
            return "";
        }
    }

    // Ensures that month and day names are titlecased (capitalized).
    private String titleCaseWord(String str) {
        if (str.length() > 0) {
            int firstChar = str.codePointAt(0);
            if (!Character.isTitleCase(firstChar)) {
                str = new String(new int[]{Character.toTitleCase(firstChar)}, 0, 1) +
                        str.substring(Character.offsetByCodePoints(str, 0, 1));
            }
        }
        return str;
    }


    /**
     * determine on which day of week idx the first of the months is
     */
    private int determineFirstOfMonthDayOfWeek() {
        // determine with which cell to start
        int firstDayOfWeek = WeekFields.of(getLocale()).getFirstDayOfWeek().getValue();
        int firstOfMonthIdx = displayedYearMonth.get().atDay(1).getDayOfWeek().getValue() - firstDayOfWeek;
        if (firstOfMonthIdx < 0) {
            firstOfMonthIdx += daysPerWeek;
        }
        return firstOfMonthIdx;
    }

    private int determineDaysInMonth(YearMonth month) {
        return month.atDay(1).plusMonths(1).minusDays(1).getDayOfMonth();
    }

    private boolean isToday(LocalDate localDate) {
        return (localDate.equals(LocalDate.now()));
    }

    protected LocalDate dayCellDate(DateCell dateCell) {
        assert (dayCellDates != null);
        return dayCellDates[dayCells.indexOf(dateCell)];
    }

    // public for behavior class
    public void goToDayCell(DateCell dateCell, int offset, ChronoUnit unit) {
        goToDate(dayCellDate(dateCell).plus(offset, unit));
    }

    protected void forward(int offset, ChronoUnit unit) {
        YearMonth yearMonth = displayedYearMonth.get();
        DateCell dateCell = lastFocusedDayCell;
        if (dateCell == null || !dayCellDate(dateCell).getMonth().equals(yearMonth.getMonth())) {
            dateCell = findDayCellForDate(yearMonth.atDay(1));
        }
        goToDayCell(dateCell, offset, unit);
    }

    // public for behavior class
    public void goToDate(LocalDate date) {
        if (isValidDate(datePicker.getChronology(), date)) {
            displayedYearMonth.set(YearMonth.from(date));
            findDayCellForDate(date).requestFocus();
        }
    }

    // public for behavior class
    public void selectDayCell(DateCell dateCell) {
        datePicker.setValue(fromLocalDate(dayCellDate(dateCell)));
//        datePicker.hide();
    }

    private DateCell findDayCellForDate(LocalDate date) {
        for (int i = 0; i < dayCellDates.length; i++) {
            if (date.equals(dayCellDates[i])) {
                return dayCells.get(i);
            }
        }
        return dayCells.get(dayCells.size() / 2 + 1);
    }

    void clearFocus() {
        LocalDate focusDate = toLocalDate(datePicker.getValue());
        if (focusDate == null) {
            focusDate = LocalDate.now();
        }
        if (YearMonth.from(focusDate).equals(displayedYearMonth.get())) {
            // focus date
            goToDate(focusDate);
        }
        else {
            // focus month spinner (should not happen)
            backMonthButton.requestFocus();
        }

        // RT-31857
        if (backMonthButton.getWidth() == 0) {
            backMonthButton.requestLayout();
            forwardMonthButton.requestLayout();
            backYearButton.requestLayout();
            forwardYearButton.requestLayout();
        }
    }

    protected void createDayCells() {
        final EventHandler<MouseEvent> dayCellActionHandler = new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent ev) {
                if (ev.getButton() != MouseButton.PRIMARY) {
                    return;
                }

                DateCell dayCell = (DateCell) ev.getSource();
                selectDayCell(dayCell);
                lastFocusedDayCell = dayCell;
            }
        };

        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < daysPerWeek; col++) {
                DateCell dayCell = createDayCell();
                dayCell.setOnMouseClicked(dayCellActionHandler);
                dayCells.add(dayCell);
            }
        }

        dayCellDates = new LocalDate[6 * daysPerWeek];
    }

    private DateCell createDayCell() {
        DateCell cell = null;
        if (datePicker.getDayCellFactory() != null) {
            cell = datePicker.getDayCellFactory().call(datePicker);
        }
        if (cell == null) {
            cell = new DateCell();
        }

        return cell;
    }

    protected Locale getLocale() {
        return Locale.getDefault(Locale.Category.FORMAT);
    }

    /**
     * The primary chronology for display. This may be overridden to be different than the DatePicker chronology. For
     * example DatePickerHijrahContent uses ISO as primary and Hijrah as a secondary chronology.
     */
    protected Chronology getPrimaryChronology() {
        return datePicker.getChronology();
    }

    protected boolean isValidDate(Chronology chrono, LocalDate date) {
        try {
            if (date != null) {
                chrono.date(date);
            }
            return true;
        }
        catch (DateTimeException ex) {
            return false;
        }
    }

    // JIDE added method are below

    /**
     * A custom cell factory can be provided to customize individual day cells in the DatePicker popup. Refer to {@link
     * DateCell} and {@link Cell} for more information on cell factories. Example:
     * <p/>
     * <pre>{@code
     * final Callback&lt;DatePicker, DateCell&gt; dayCellFactory = new Callback&lt;DatePicker, DateCell&gt;() {
     *     public DateCell call(final DatePicker datePicker) {
     *         return new DateCell() {
     *             &#064;Override public void updateItem(LocalDate item, boolean empty) {
     *                 super.updateItem(item, empty);
     *                 if (MonthDay.from(item).equals(MonthDay.of(9, 25))) {
     *                     setTooltip(new Tooltip("Happy Birthday!"));
     *                     setStyle("-fx-background-color: #ff4444;");
     *                 }
     *                 if (item.equals(LocalDate.now().plusDays(1))) {
     *                     // Tomorrow is too soon.
     *                     setDisable(true);
     *                 }
     *             }
     *         };
     *     }
     * };
     * datePicker.setDayCellFactory(dayCellFactory);
     * }</pre>
     */
    private ObjectProperty<Callback<AbstractDatePopupContent, DateCell>> dayCellFactory;

    public final void setDayCellFactory(Callback<AbstractDatePopupContent, DateCell> value) {
        dayCellFactoryProperty().set(value);
    }

    public final Callback<AbstractDatePopupContent, DateCell> getDayCellFactory() {
        return (dayCellFactory != null) ? dayCellFactory.get() : null;
    }

    public final ObjectProperty<Callback<AbstractDatePopupContent, DateCell>> dayCellFactoryProperty() {
        if (dayCellFactory == null) {
            dayCellFactory = new SimpleObjectProperty<Callback<AbstractDatePopupContent, DateCell>>(this, "dayCellFactory") { //NON-NLS
                @Override
                protected void invalidated() {
                    super.invalidated();
                    updateGrid();
                }
            };
        }
        return dayCellFactory;
    }

    /**
     * The calendar system used for parsing, displaying, and choosing dates in the DatePicker control.
     * <p/>
     * <p>The default value is returned from a call to Chronology.ofLocale(Locale.getDefault(Locale.Category.FORMAT)).
     * The default is usually {@link IsoChronology} unless provided explicitly in the {@link Locale} by use of a Locale
     * calendar extension.
     * <p/>
     * Setting the value to {@code null} will restore the default chronology.
     */
    public final ObjectProperty<Chronology> chronologyProperty() {
        return chronology;
    }

    private ObjectProperty<Chronology> chronology =
            new SimpleObjectProperty<>(this, "chronology", null); //NON-NLS

    public final Chronology getChronology() {
        Chronology chrono = chronology.get();
        if (chrono == null) {
            try {
                chrono = Chronology.ofLocale(Locale.getDefault(Locale.Category.FORMAT));
            }
            catch (Exception ex) {
                System.err.println(ex);
            }
            if (chrono == null) {
                chrono = IsoChronology.INSTANCE;
            }
            //System.err.println(chrono);
        }
        return chrono;
    }

    public final void setChronology(Chronology value) {
        chronology.setValue(value);
    }

    /**
     * @treatAsPrivate implementation detail
     */
    private static class StyleableProperties {
        private static final String country =
                Locale.getDefault(Locale.Category.FORMAT).getCountry();
        private static final CssMetaData<AbstractDatePopupContent, Boolean> SHOW_WEEK_NUMBERS =
                new CssMetaData<AbstractDatePopupContent, Boolean>("-fx-show-week-numbers",
                        BooleanConverter.getInstance(),
                        (!country.isEmpty() &&
                                ControlResources.getNonTranslatableString("DatePicker.showWeekNumbers").contains(country))) {
                    @Override
                    public boolean isSettable(AbstractDatePopupContent n) {
                        return n.showWeekNumbers == null || !n.showWeekNumbers.isBound();
                    }

                    @Override
                    public StyleableProperty<Boolean> getStyleableProperty(AbstractDatePopupContent n) {
                        return (StyleableProperty) n.showWeekNumbersProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            final List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<CssMetaData<? extends Styleable, ?>>(Control.getClassCssMetaData());
            Collections.addAll(styleables,
                    SHOW_WEEK_NUMBERS
            );
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

    /**
     * Whether the DatePicker popup should display a column showing week numbers.
     * <p/>
     * <p>The default value is false unless otherwise defined in a resource bundle for the current locale.
     * <p/>
     * <p>This property may be toggled by the end user by using a context menu in the DatePicker popup, so it is
     * recommended that applications save and restore the value between sessions.
     */
    public final BooleanProperty showWeekNumbersProperty() {
        if (showWeekNumbers == null) {
            String country = Locale.getDefault(Locale.Category.FORMAT).getCountry();
            boolean localizedDefault =
                    (!country.isEmpty() &&
                            ControlResources.getNonTranslatableString("DatePicker.showWeekNumbers").contains(country));
            showWeekNumbers = new StyleableBooleanProperty(localizedDefault) {
                @Override
                protected void invalidated() {
                    super.invalidated();
                    updateGrid();
                    updateWeeknumberDateCells();
                }

                @Override
                public CssMetaData<AbstractDatePopupContent, Boolean> getCssMetaData() {
                    return StyleableProperties.SHOW_WEEK_NUMBERS;
                }

                @Override
                public Object getBean() {
                    return AbstractDatePopupContent.this;
                }

                @Override
                public String getName() {
                    return "showWeekNumbers";
                }
            };
        }
        return showWeekNumbers;
    }

    private BooleanProperty showWeekNumbers;

    public final void setShowWeekNumbers(boolean value) {
        showWeekNumbersProperty().setValue(value);
    }

    public final boolean isShowWeekNumbers() {
        return showWeekNumbersProperty().getValue();
    }

// Copied from ComboBoxBase

    /**
     * The value of this ComboBox is defined as the selected item if the input is not editable, or if it is editable,
     * the most recent user action: either the value input by the user, or the last selected item.
     */
    @Override
    public final ObjectProperty<T> valueProperty() {
        return value;
    }

    private ObjectProperty<T> value = new SimpleObjectProperty<T>(this, "value") { //NON-NLS
        @Override
        protected void invalidated() {
            super.invalidated();
            updateDisplayedYearMonth();
            updateDayCells();
            clearFocus();
        }
    };

    @Override
    public final void setValue(T value) {
        valueProperty().set(value);
    }

    @Override
    public final T getValue() {
        return valueProperty().get();
    }

    /**
     * Subclass override this method to convert from the LocalDate to the value type that is being edited.
     */
    protected abstract T fromLocalDate(LocalDate localDate);

    /**
     * Subclass override this method to convert from the value type that is being edited to the LocalDate.
     */
    protected abstract LocalDate toLocalDate(T value);

    private void updateDisplayedYearMonth() {
        LocalDate date = toLocalDate(getValue());
        displayedYearMonth.set((date != null) ? YearMonth.from(date) : YearMonth.now());
    }

    // Copied from Utils
    static final Text helper = new Text();
    static final double DEFAULT_WRAPPING_WIDTH = helper.getWrappingWidth();
    static final double DEFAULT_LINE_SPACING = helper.getLineSpacing();
    static final String DEFAULT_TEXT = helper.getText();

    static double computeTextWidth(Font font, String text, double wrappingWidth) {
        helper.setText(text);
        helper.setFont(font);
        // Note that the wrapping width needs to be set to zero before
        // getting the text's real preferred width.
        helper.setWrappingWidth(0);
        helper.setLineSpacing(0);
        double w = Math.min(helper.prefWidth(-1), wrappingWidth);
        helper.setWrappingWidth((int) Math.ceil(w));
        w = Math.ceil(helper.getLayoutBounds().getWidth());
        // RESTORE STATE
        helper.setWrappingWidth(DEFAULT_WRAPPING_WIDTH);
        helper.setLineSpacing(DEFAULT_LINE_SPACING);
        helper.setText(DEFAULT_TEXT);
        return w;
    }

}
