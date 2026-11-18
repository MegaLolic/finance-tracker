import type { Category, Currency, Transaction } from "../types";

export type ReportPeriod = "DAY" | "WEEK" | "MONTH" | "YEAR" | "CUSTOM";

export interface ReportCategory {
  categoryId: number;
  categoryType: "INCOME" | "EXPENSE";
  totalAmount: number;
  transactionCount: number;
}

export interface ReportTotals {
  totalIncome: number;
  totalExpense: number;
  netAmount: number;
  incomeCount: number;
  expenseCount: number;
  transactionCount: number;
  averageIncome: number;
  averageExpense: number;
  categories: ReportCategory[];
}

export interface TrendPoint {
  date: string;
  label: string;
  fullLabel: string;
  income: number;
  expense: number;
}

export function localDate(date: Date = new Date()): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function parseDate(value: string): Date {
  const [year, month, day] = value.split("-").map(Number);
  const date = new Date(year, month - 1, day);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || localDate(date) !== value) {
    throw new RangeError("Укажите корректную дату");
  }
  return date;
}

export function rangeFor(
  period: ReportPeriod,
  anchor: string,
  customFrom?: string,
  customTo?: string,
): { from: string; to: string } {
  if (period === "CUSTOM") {
    const from = customFrom || anchor;
    const to = customTo || anchor;
    parseDate(from);
    parseDate(to);
    if (from > to)
      throw new RangeError("Начало периода должно быть раньше его окончания");
    return { from, to };
  }

  const from = parseDate(anchor);
  const to = parseDate(anchor);
  if (period === "WEEK") {
    from.setDate(from.getDate() - ((from.getDay() + 6) % 7));
    to.setTime(from.getTime());
    to.setDate(to.getDate() + 6);
  } else if (period === "MONTH") {
    from.setDate(1);
    to.setMonth(to.getMonth() + 1, 0);
  } else if (period === "YEAR") {
    from.setMonth(0, 1);
    to.setMonth(11, 31);
  }
  return { from: localDate(from), to: localDate(to) };
}

export function filterTransactions(
  transactions: Transaction[],
  accountId: number,
  from: string,
  to: string,
): Transaction[] {
  return transactions.filter((transaction) => {
    const date = localDate(new Date(transaction.createdAt));
    return transaction.accountId === accountId && date >= from && date <= to;
  });
}

export function formatMoney(amount: number, currency: Currency): string {
  return new Intl.NumberFormat("ru-RU", {
    style: "currency",
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(amount);
}

export function reportTotals(
  transactions: Transaction[],
  categories: Category[],
): ReportTotals {
  const categoryTypes = new Map(
    categories.map((category) => [category.id, category.type]),
  );
  const grouped = new Map<number, ReportCategory & { cents: number }>();
  let income = 0;
  let expense = 0;
  let incomeCount = 0;
  let expenseCount = 0;

  for (const transaction of transactions) {
    const type = categoryTypes.get(transaction.categoryId);
    if (!type) continue;
    const amount = Math.round(Number(transaction.amount) * 100);
    if (type === "INCOME") {
      income += amount;
      incomeCount += 1;
    } else {
      expense += amount;
      expenseCount += 1;
    }
    const category = grouped.get(transaction.categoryId) || {
      categoryId: transaction.categoryId,
      categoryType: type,
      totalAmount: 0,
      transactionCount: 0,
      cents: 0,
    };
    category.cents += amount;
    category.transactionCount += 1;
    grouped.set(category.categoryId, category);
  }

  return {
    totalIncome: income / 100,
    totalExpense: expense / 100,
    netAmount: (income - expense) / 100,
    incomeCount,
    expenseCount,
    transactionCount: incomeCount + expenseCount,
    averageIncome: incomeCount ? Math.round(income / incomeCount) / 100 : 0,
    averageExpense: expenseCount ? Math.round(expense / expenseCount) / 100 : 0,
    categories: [...grouped.values()]
      .sort((a, b) => a.categoryId - b.categoryId)
      .map(({ cents, ...category }) => ({
        ...category,
        totalAmount: cents / 100,
      })),
  };
}

export function trendData(
  transactions: Transaction[],
  categories: Category[],
  from: string,
  to: string,
): TrendPoint[] {
  const start = parseDate(from);
  const end = parseDate(to);
  if (start > end) return [];
  const span =
    Math.round(
      (Date.UTC(end.getFullYear(), end.getMonth(), end.getDate()) -
        Date.UTC(start.getFullYear(), start.getMonth(), start.getDate())) /
        86_400_000,
    ) + 1;
  const monthSpan =
    (end.getFullYear() - start.getFullYear()) * 12 +
    end.getMonth() -
    start.getMonth() +
    1;
  const bucket = span <= 93 ? "day" : monthSpan <= 60 ? "month" : "year";
  const keyFor = (date: Date): string =>
    bucket === "day"
      ? localDate(date)
      : bucket === "month"
        ? localDate(date).slice(0, 7)
        : String(date.getFullYear());
  const points = new Map<
    string,
    TrendPoint & { incomeCents: number; expenseCents: number }
  >();
  const cursor = new Date(start);
  if (bucket === "month") cursor.setDate(1);
  if (bucket === "year") cursor.setMonth(0, 1);

  while (cursor <= end) {
    const labelOptions: Intl.DateTimeFormatOptions =
      bucket === "day"
        ? { day: "numeric", month: "short" }
        : bucket === "month"
          ? { month: "short", year: monthSpan > 12 ? "2-digit" : undefined }
          : { year: "numeric" };
    points.set(keyFor(cursor), {
      date: localDate(cursor),
      label: cursor
        .toLocaleDateString("ru-RU", labelOptions)
        .replaceAll(".", ""),
      fullLabel: cursor.toLocaleDateString(
        "ru-RU",
        bucket === "day"
          ? { day: "numeric", month: "long", year: "numeric" }
          : bucket === "month"
            ? { month: "long", year: "numeric" }
            : { year: "numeric" },
      ),
      income: 0,
      expense: 0,
      incomeCents: 0,
      expenseCents: 0,
    });
    if (bucket === "day") cursor.setDate(cursor.getDate() + 1);
    else if (bucket === "month") cursor.setMonth(cursor.getMonth() + 1);
    else cursor.setFullYear(cursor.getFullYear() + 1);
  }

  const categoryTypes = new Map(
    categories.map((category) => [category.id, category.type]),
  );
  for (const transaction of transactions) {
    const date = new Date(transaction.createdAt);
    const day = localDate(date);
    if (day < from || day > to) continue;
    const point = points.get(keyFor(date));
    const type = categoryTypes.get(transaction.categoryId);
    if (!point || !type) continue;
    if (type === "INCOME")
      point.incomeCents += Math.round(Number(transaction.amount) * 100);
    else point.expenseCents += Math.round(Number(transaction.amount) * 100);
  }

  return [...points.values()].map(
    ({ incomeCents, expenseCents, ...point }) => ({
      ...point,
      income: incomeCents / 100,
      expense: expenseCents / 100,
    }),
  );
}
