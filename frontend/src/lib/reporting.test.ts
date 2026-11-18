import process from "node:process";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import type { Category, Transaction } from "../types";
import {
  filterTransactions,
  localDate,
  rangeFor,
  reportTotals,
  trendData,
} from "./reporting";

const categories: Category[] = [
  { id: 1, name: "Зарплата", type: "INCOME" },
  { id: 2, name: "Продукты", type: "EXPENSE" },
  { id: 3, name: "Транспорт", type: "EXPENSE" },
];

function transaction(
  id: number,
  amount: number,
  categoryId: number,
  createdAt = "2026-11-18T10:00:00Z",
  accountId = 10,
): Transaction {
  return {
    id,
    version: 0,
    accountId,
    categoryId,
    amount,
    description: null,
    createdAt,
  };
}

let previousTimezone: string | undefined;

beforeEach(() => {
  previousTimezone = process.env.TZ;
  process.env.TZ = "Europe/Berlin";
});

afterEach(() => {
  if (previousTimezone === undefined) delete process.env.TZ;
  else process.env.TZ = previousTimezone;
});

describe("calendar report periods", () => {
  it.each([
    ["2024-02-18", "2024-02-29"],
    ["2023-02-18", "2023-02-28"],
    ["2000-02-18", "2000-02-29"],
    ["2100-02-18", "2100-02-28"],
  ])("handles leap and century rules for %s", (anchor, lastDay) => {
    expect(rangeFor("MONTH", anchor)).toEqual({
      from: `${anchor.slice(0, 7)}-01`,
      to: lastDay,
    });
  });

  it.each(["2021-01-01", "2020-12-28", "2021-01-03"])(
    "uses Monday–Sunday across years for %s",
    (anchor) => {
      expect(rangeFor("WEEK", anchor)).toEqual({
        from: "2020-12-28",
        to: "2021-01-03",
      });
    },
  );

  it("returns a complete calendar year and a single selected day", () => {
    expect(rangeFor("YEAR", "2024-02-29")).toEqual({
      from: "2024-01-01",
      to: "2024-12-31",
    });
    expect(rangeFor("DAY", "2024-02-29")).toEqual({
      from: "2024-02-29",
      to: "2024-02-29",
    });
  });

  it("keeps both custom endpoints inclusive and allows a one-day custom period", () => {
    expect(
      rangeFor("CUSTOM", "2026-11-18", "2026-10-01", "2026-11-18"),
    ).toEqual({ from: "2026-10-01", to: "2026-11-18" });
    expect(
      rangeFor("CUSTOM", "2026-11-18", "2026-11-18", "2026-11-18"),
    ).toEqual({ from: "2026-11-18", to: "2026-11-18" });
  });

  it.each([
    "2026-02-30",
    "2026-13-01",
    "2026-00-01",
    "2026-11-00",
    "18.11.2026",
    "",
  ])("rejects invalid dates without JavaScript rollover: %s", (anchor) => {
    expect(() => rangeFor("DAY", anchor)).toThrow(RangeError);
  });

  it("rejects a reversed custom range", () => {
    expect(() =>
      rangeFor("CUSTOM", "2026-11-18", "2026-11-19", "2026-11-18"),
    ).toThrow(RangeError);
  });
});

describe("local date boundaries and account isolation", () => {
  it("formats the local date instead of the UTC date", () => {
    expect(localDate(new Date("2026-11-17T23:30:00Z"))).toBe("2026-11-18");
  });

  it.each([
    [
      "2026-03-29",
      "2026-03-28T22:59:59.999Z",
      "2026-03-28T23:00:00Z",
      "2026-03-29T21:59:59.999Z",
      "2026-03-29T22:00:00Z",
    ],
    [
      "2026-10-25",
      "2026-10-24T21:59:59.999Z",
      "2026-10-24T22:00:00Z",
      "2026-10-25T22:59:59.999Z",
      "2026-10-25T23:00:00Z",
    ],
  ])(
    "includes the whole %s even on a 23/25-hour day",
    (day, before, start, lastMillisecond, nextDay) => {
      const operations = [
        transaction(1, 1, 2, before),
        transaction(2, 1, 2, start),
        transaction(3, 1, 2, lastMillisecond),
        transaction(4, 1, 2, nextDay),
        transaction(5, 500, 2, start, 20),
      ];
      expect(
        filterTransactions(operations, 10, day, day).map(
          (operation) => operation.id,
        ),
      ).toEqual([2, 3]);
    },
  );

  it("does not mutate input data or include another account", () => {
    const operations = Object.freeze([
      Object.freeze(transaction(1, 10, 1)),
      Object.freeze(transaction(2, 999, 1, undefined, 20)),
    ]);
    expect(
      filterTransactions([...operations], 10, "2026-11-01", "2026-11-30").map(
        (operation) => operation.id,
      ),
    ).toEqual([1]);
    expect(operations.map((operation) => operation.id)).toEqual([1, 2]);
  });
});

describe("money totals", () => {
  it("adds decimal amounts in cents and computes independent category totals", () => {
    const totals = reportTotals(
      [
        transaction(1, 0.1, 1),
        transaction(2, 0.2, 1),
        transaction(3, 0.01, 2),
        transaction(4, 0.02, 2),
        transaction(5, 0.01, 3),
      ],
      categories,
    );

    expect(totals).toEqual({
      totalIncome: 0.3,
      totalExpense: 0.04,
      netAmount: 0.26,
      incomeCount: 2,
      expenseCount: 3,
      transactionCount: 5,
      averageIncome: 0.15,
      averageExpense: 0.01,
      categories: [
        {
          categoryId: 1,
          categoryType: "INCOME",
          totalAmount: 0.3,
          transactionCount: 2,
        },
        {
          categoryId: 2,
          categoryType: "EXPENSE",
          totalAmount: 0.03,
          transactionCount: 2,
        },
        {
          categoryId: 3,
          categoryType: "EXPENSE",
          totalAmount: 0.01,
          transactionCount: 1,
        },
      ],
    });
  });

  it("rounds average half-cents up and permits a negative net amount", () => {
    const totals = reportTotals(
      [transaction(1, 0.01, 2), transaction(2, 0.02, 2)],
      categories,
    );
    expect(totals.averageExpense).toBe(0.02);
    expect(totals.averageIncome).toBe(0);
    expect(totals.netAmount).toBe(-0.03);
  });

  it("returns zeros rather than NaN or an invented category for an empty period", () => {
    expect(reportTotals([], categories)).toEqual({
      totalIncome: 0,
      totalExpense: 0,
      netAmount: 0,
      incomeCount: 0,
      expenseCount: 0,
      transactionCount: 0,
      averageIncome: 0,
      averageExpense: 0,
      categories: [],
    });
  });
});

describe("chart series", () => {
  it("fills empty local days and puts adjacent UTC dates into their local bucket", () => {
    const data = trendData(
      [
        transaction(1, 0.1, 1, "2026-11-17T23:15:00Z"),
        transaction(2, 0.2, 1, "2026-11-18T10:00:00Z"),
        transaction(3, 0.05, 2, "2026-11-18T11:00:00Z"),
        transaction(4, 999, 1, "2026-11-20T10:00:00Z"),
      ],
      categories,
      "2026-11-17",
      "2026-11-19",
    );

    expect(
      data.map(({ date, income, expense }) => ({ date, income, expense })),
    ).toEqual([
      { date: "2026-11-17", income: 0, expense: 0 },
      { date: "2026-11-18", income: 0.3, expense: 0.05 },
      { date: "2026-11-19", income: 0, expense: 0 },
    ]);
  });

  it("keeps one bucket per calendar day across a DST transition", () => {
    const data = trendData(
      [
        transaction(1, 1, 2, "2026-03-28T23:00:00Z"),
        transaction(2, 2, 2, "2026-03-29T22:00:00Z"),
      ],
      categories,
      "2026-03-28",
      "2026-03-30",
    );
    expect(data.map(({ date, expense }) => ({ date, expense }))).toEqual([
      { date: "2026-03-28", expense: 0 },
      { date: "2026-03-29", expense: 1 },
      { date: "2026-03-30", expense: 2 },
    ]);
  });

  it("groups a calendar year by month, keeping all twelve empty/data buckets", () => {
    const data = trendData(
      [
        transaction(1, 1.11, 1, "2026-11-01T10:00:00Z"),
        transaction(2, 2.22, 1, "2026-11-30T10:00:00Z"),
        transaction(3, 0.33, 2, "2026-12-01T10:00:00Z"),
      ],
      categories,
      "2026-01-01",
      "2026-12-31",
    );
    expect(data).toHaveLength(12);
    expect(data[0]).toMatchObject({
      date: "2026-01-01",
      income: 0,
      expense: 0,
    });
    expect(data[10]).toMatchObject({
      date: "2026-11-01",
      income: 3.33,
      expense: 0,
    });
    expect(data[11]).toMatchObject({
      date: "2026-12-01",
      income: 0,
      expense: 0.33,
    });
  });

  it("uses bounded yearly buckets for a large history while preserving the total", () => {
    const data = trendData(
      [
        transaction(1, 1.11, 1, "2021-06-15T10:00:00Z"),
        transaction(2, 2.22, 1, "2021-07-15T10:00:00Z"),
        transaction(3, 4.44, 2, "2029-01-01T10:00:00Z"),
      ],
      categories,
      "2020-01-01",
      "2030-12-31",
    );
    expect(data).toHaveLength(11);
    expect(data[1]).toMatchObject({
      date: "2021-01-01",
      income: 3.33,
      expense: 0,
    });
    expect(data[9]).toMatchObject({
      date: "2029-01-01",
      income: 0,
      expense: 4.44,
    });
    expect(
      data.reduce((sum, point) => sum + Math.round(point.income * 100), 0),
    ).toBe(333);
  });
});
