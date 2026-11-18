import { useId, useMemo } from "react";
import {
  Area,
  AreaChart,
  CartesianGrid,
  Cell,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { ChartNoAxesCombined, CircleDashed, Info } from "lucide-react";
import type { Category, Currency, Transaction } from "../types";
import { formatMoney, reportTotals, trendData } from "../lib/reporting";
import "./ReportCharts.css";

interface ReportChartsProps {
  transactions: Transaction[];
  categories: Category[];
  currency: Currency;
  from: string;
  to: string;
}

const palette = [
  "#635bff",
  "#a99af8",
  "#e5b65c",
  "#61bcb5",
  "#e99baf",
  "#8aa5e8",
  "#b4cba5",
];

function compactAmount(value: number): string {
  if (Math.abs(value) >= 1_000_000)
    return `${new Intl.NumberFormat("ru-RU", { maximumFractionDigits: 1 }).format(value / 1_000_000)} млн`;
  if (Math.abs(value) >= 10_000)
    return `${new Intl.NumberFormat("ru-RU", { maximumFractionDigits: 0 }).format(value / 1_000)} тыс`;
  return new Intl.NumberFormat("ru-RU", { maximumFractionDigits: 0 }).format(
    value,
  );
}

export default function ReportCharts({
  transactions,
  categories,
  currency,
  from,
  to,
}: ReportChartsProps) {
  const id = useId().replaceAll(":", "");
  const trend = useMemo(
    () => trendData(transactions, categories, from, to),
    [transactions, categories, from, to],
  );
  const totals = useMemo(
    () => reportTotals(transactions, categories),
    [transactions, categories],
  );
  const spending = useMemo(
    () =>
      totals.categories
        .filter((category) => category.categoryType === "EXPENSE")
        .sort((a, b) => b.totalAmount - a.totalAmount)
        .map((category, index) => ({
          ...category,
          name:
            categories.find((item) => item.id === category.categoryId)?.name ||
            "Без категории",
          color: palette[index % palette.length],
          share: totals.totalExpense
            ? (category.totalAmount / totals.totalExpense) * 100
            : 0,
        })),
    [totals, categories],
  );
  const hasTransactions = totals.transactionCount > 0;

  return (
    <div className="report-charts">
      <section
        className="chart-card chart-trend-card"
        aria-labelledby={`trend-title-${id}`}
      >
        <header className="chart-card-header">
          <div>
            <h2 id={`trend-title-${id}`}>Движение денег</h2>
            <p>Доходы и расходы за выбранный период</p>
          </div>
          <span className="chart-unit">{currency}</span>
        </header>
        <div className="chart-key" aria-label="Обозначения графика">
          <span>
            <i style={{ background: "#16a085" }} />
            Доходы
          </span>
          <span>
            <i style={{ background: "#635bff" }} />
            Расходы
          </span>
        </div>
        {hasTransactions ? (
          <div
            className="chart-trend"
            role="img"
            aria-label={`График движения денег: доходы ${formatMoney(totals.totalIncome, currency)}, расходы ${formatMoney(totals.totalExpense, currency)}`}
          >
            <ResponsiveContainer width="100%" height="100%" minWidth={0}>
              <AreaChart
                data={trend}
                margin={{ top: 15, right: 12, left: -14, bottom: 0 }}
                accessibilityLayer
              >
                <defs>
                  <linearGradient
                    id={`income-${id}`}
                    x1="0"
                    y1="0"
                    x2="0"
                    y2="1"
                  >
                    <stop offset="0%" stopColor="#16a085" stopOpacity={0.13} />
                    <stop offset="100%" stopColor="#16a085" stopOpacity={0} />
                  </linearGradient>
                  <linearGradient
                    id={`expense-${id}`}
                    x1="0"
                    y1="0"
                    x2="0"
                    y2="1"
                  >
                    <stop offset="0%" stopColor="#635bff" stopOpacity={0.17} />
                    <stop
                      offset="100%"
                      stopColor="#635bff"
                      stopOpacity={0.015}
                    />
                  </linearGradient>
                </defs>
                <CartesianGrid
                  stroke="#eeeff5"
                  vertical={false}
                  strokeDasharray="3 5"
                />
                <XAxis
                  dataKey="label"
                  tickLine={false}
                  axisLine={false}
                  tick={{ fill: "#9295a6", fontSize: 11 }}
                  minTickGap={35}
                  tickMargin={12}
                />
                <YAxis
                  tickLine={false}
                  axisLine={false}
                  tick={{ fill: "#9295a6", fontSize: 11 }}
                  tickFormatter={compactAmount}
                  width={64}
                />
                <Tooltip
                  cursor={{ stroke: "#d3d0f2", strokeDasharray: "4 4" }}
                  content={({ active, payload }) => {
                    if (!active || !payload?.length) return null;
                    return (
                      <div className="chart-tooltip">
                        <strong>
                          {String(payload[0]?.payload?.fullLabel || "")}
                        </strong>
                        {payload.map((item) => (
                          <div key={String(item.dataKey)}>
                            <span>
                              <i style={{ background: item.color }} />
                              {item.name}
                            </span>
                            <b>
                              {formatMoney(Number(item.value || 0), currency)}
                            </b>
                          </div>
                        ))}
                      </div>
                    );
                  }}
                />
                <Area
                  type="monotone"
                  name="Доходы"
                  dataKey="income"
                  stroke="#16a085"
                  strokeWidth={2.5}
                  fill={`url(#income-${id})`}
                  isAnimationActive={false}
                  dot={trend.length === 1 ? { r: 4, fill: "#16a085" } : false}
                  activeDot={{ r: 5, stroke: "#fff", strokeWidth: 3 }}
                />
                <Area
                  type="monotone"
                  name="Расходы"
                  dataKey="expense"
                  stroke="#635bff"
                  strokeWidth={2.5}
                  fill={`url(#expense-${id})`}
                  isAnimationActive={false}
                  dot={trend.length === 1 ? { r: 4, fill: "#635bff" } : false}
                  activeDot={{ r: 5, stroke: "#fff", strokeWidth: 3 }}
                />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        ) : (
          <div className="chart-empty">
            <span>
              <ChartNoAxesCombined size={27} strokeWidth={1.5} />
            </span>
            <h3>Здесь появится ваша история</h3>
            <p>
              Добавьте первую операцию, чтобы увидеть движение денег за этот
              период.
            </p>
          </div>
        )}
        <footer className="chart-footnote">
          <Info size={13} />
          <span>График показывает операции по дате их создания.</span>
        </footer>
      </section>

      <section
        className="chart-card chart-category-card"
        aria-labelledby={`categories-title-${id}`}
      >
        <header className="chart-card-header">
          <div>
            <h2 id={`categories-title-${id}`}>На что уходят деньги</h2>
            <p>Расходы по категориям</p>
          </div>
          <span className="chart-unit">{spending.length} катег.</span>
        </header>
        {spending.length ? (
          <>
            <div
              className="chart-donut"
              role="img"
              aria-label={`Расходы по ${spending.length} категориям, всего ${formatMoney(totals.totalExpense, currency)}`}
            >
              <ResponsiveContainer width="100%" height="100%" minWidth={0}>
                <PieChart accessibilityLayer>
                  <Pie
                    data={spending}
                    dataKey="totalAmount"
                    nameKey="name"
                    innerRadius="69%"
                    outerRadius="91%"
                    paddingAngle={spending.length > 1 ? 4 : 0}
                    cornerRadius={5}
                    stroke="none"
                    startAngle={90}
                    endAngle={-270}
                    isAnimationActive={false}
                  >
                    {spending.map((category) => (
                      <Cell key={category.categoryId} fill={category.color} />
                    ))}
                  </Pie>
                  <Tooltip
                    content={({ active, payload }) => {
                      if (!active || !payload?.length) return null;
                      const category = payload[0]?.payload;
                      return (
                        <div className="chart-tooltip">
                          <strong>{String(category?.name || "")}</strong>
                          <div>
                            <span>Расходы</span>
                            <b>
                              {formatMoney(
                                Number(category?.totalAmount || 0),
                                currency,
                              )}
                            </b>
                          </div>
                          <small>
                            {Number(category?.share || 0)
                              .toFixed(1)
                              .replace(".", ",")}
                            % всех расходов
                          </small>
                        </div>
                      );
                    }}
                  />
                </PieChart>
              </ResponsiveContainer>
              <div className="chart-donut-center">
                <span>Всего расходов</span>
                <strong>{formatMoney(totals.totalExpense, currency)}</strong>
                <small>{totals.expenseCount} операций</small>
              </div>
            </div>
            <ul className="chart-category-legend">
              {spending.map((category) => (
                <li key={category.categoryId}>
                  <span className="chart-category-name">
                    <i style={{ background: category.color }} />
                    <span title={category.name}>{category.name}</span>
                  </span>
                  <span className="chart-category-value">
                    <b>{formatMoney(category.totalAmount, currency)}</b>
                    <small>
                      {new Intl.NumberFormat("ru-RU", {
                        maximumFractionDigits: 1,
                      }).format(category.share)}
                      %
                    </small>
                  </span>
                </li>
              ))}
            </ul>
          </>
        ) : (
          <div className="chart-empty chart-empty-donut">
            <span>
              <CircleDashed size={31} strokeWidth={1.5} />
            </span>
            <h3>Пока без расходов</h3>
            <p>
              Когда появятся расходы, здесь будет их распределение по
              категориям.
            </p>
          </div>
        )}
      </section>
    </div>
  );
}
