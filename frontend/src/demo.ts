import type { DataSet, Transaction } from "./types";

export function createDemo(): DataSet {
  const now = new Date();
  const categories = [
    { id: 1, name: "Зарплата", type: "INCOME" as const },
    { id: 2, name: "Фриланс", type: "INCOME" as const },
    { id: 3, name: "Продукты", type: "EXPENSE" as const },
    { id: 4, name: "Кафе и рестораны", type: "EXPENSE" as const },
    { id: 5, name: "Покупки", type: "EXPENSE" as const },
    { id: 6, name: "Транспорт", type: "EXPENSE" as const },
    { id: 7, name: "Дом и подписки", type: "EXPENSE" as const },
  ];
  const transactions: Transaction[] = [];
  let id = 1;
  for (let offset = 60; offset >= 0; offset--) {
    const date = new Date(
      now.getFullYear(),
      now.getMonth(),
      now.getDate() - offset,
      12,
    );
    const day = date.getDate();
    const add = (
      categoryId: number,
      amount: number,
      description: string,
      accountId = 1,
    ) => {
      transactions.push({
        id: id++,
        version: 0,
        accountId,
        categoryId,
        amount,
        description,
        createdAt: date.toISOString(),
      });
    };
    if (day === 5 || day === 18) add(1, 1850, "Зарплата · основная работа");
    if (day === 12 || day === 26) add(2, 460, "Оплата проекта");
    if (day % 3 === 0) add(3, 48 + day * 1.8, "Покупки на неделю");
    if (day % 4 === 0) add(4, 22 + day * 0.7, "Кофе и обед");
    if (day % 7 === 0) add(5, 90 + day * 2.1, "Немного для себя");
    if (day % 2 === 0) add(6, 8.5 + day * 0.15, "Поездки по городу");
    if (day === 10) add(7, 580, "Аренда и подписки");
    if (day === 15) add(2, 300, "Небольшой проект", 2);
  }
  const balance = transactions
    .filter((t) => t.accountId === 1)
    .reduce((sum, t) => sum + (t.categoryId <= 2 ? t.amount : -t.amount), 850);
  return {
    user: {
      id: 1,
      name: "Александр",
      surname: "Иванов",
      email: "demo@example.com",
    },
    accounts: [
      {
        id: 1,
        version: 0,
        name: "Основной счёт",
        balance: Math.round(balance * 100) / 100,
        currency: "BYN",
      },
      { id: 2, version: 0, name: "Накопления", balance: 2400, currency: "USD" },
      {
        id: 3,
        version: 0,
        name: "На путешествия",
        balance: 1250,
        currency: "EUR",
      },
    ],
    categories,
    transactions,
  };
}
