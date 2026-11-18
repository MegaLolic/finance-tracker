export type Currency = "BYN" | "USD" | "EUR" | "RUB";
export type CategoryType = "INCOME" | "EXPENSE";

export interface User {
  id: number;
  name: string;
  surname: string;
  email: string;
}

export interface Account {
  id: number;
  version: number;
  name: string;
  balance: number;
  currency: Currency;
}

export interface Category {
  id: number;
  name: string;
  type: CategoryType;
}

export interface Transaction {
  id: number;
  version: number;
  accountId: number;
  categoryId: number;
  amount: number;
  description: string | null;
  createdAt: string;
}

export interface CategorySummary {
  categoryId: number;
  categoryType: CategoryType;
  totalAmount: number;
  transactionCount: number;
}

export interface Summary {
  accountId: number;
  from: string;
  to: string;
  timezone: string;
  totalIncome: number;
  totalExpense: number;
  netAmount: number;
  incomeCount: number;
  expenseCount: number;
  transactionCount: number;
  averageIncome: number;
  averageExpense: number;
  categories: CategorySummary[];
}

export interface DataSet {
  user: User;
  accounts: Account[];
  categories: Category[];
  transactions: Transaction[];
}

export interface Credentials {
  email: string;
  password: string;
}

export interface RegisterInput extends Credentials {
  name: string;
  surname: string;
}

export type AccountInput = Pick<Account, "name" | "balance" | "currency">;
export type CategoryInput = Pick<Category, "name" | "type">;
export type TransactionInput = Pick<
  Transaction,
  "accountId" | "categoryId" | "amount" | "description"
>;
