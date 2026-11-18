import type {
  Account,
  AccountInput,
  Category,
  CategoryInput,
  Credentials,
  DataSet,
  RegisterInput,
  Summary,
  Transaction,
  TransactionInput,
  User,
} from "./types";

interface TokenPair {
  token: string;
  refreshToken: string;
}

interface ErrorResponse {
  message?: string;
  errors?: Record<string, string>;
}

export class ApiError extends Error {
  readonly status: number;
  readonly errors: Record<string, string>;

  constructor(
    status: number,
    message: string,
    errors: Record<string, string> = {},
  ) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.errors = errors;
  }
}

const SESSION_KEY = "finance-assistance.session";
let session: TokenPair | null = readSession();
let sessionRevision = 0;
let refreshPromise: Promise<void> | null = null;

function isTokenPair(value: unknown): value is TokenPair {
  if (!value || typeof value !== "object") return false;
  const pair = value as Partial<TokenPair>;
  return (
    typeof pair.token === "string" &&
    pair.token.length > 0 &&
    typeof pair.refreshToken === "string" &&
    pair.refreshToken.length > 0
  );
}

function readSession(): TokenPair | null {
  try {
    const stored = sessionStorage.getItem(SESSION_KEY);
    const value: unknown = stored ? JSON.parse(stored) : null;
    return isTokenPair(value) ? value : null;
  } catch {
    return null;
  }
}

function saveSession(value: TokenPair | null): void {
  session = value;
  sessionRevision++;
  try {
    if (value) sessionStorage.setItem(SESSION_KEY, JSON.stringify(value));
    else sessionStorage.removeItem(SESSION_KEY);
  } catch {
    // Private browser modes may disable storage; the current in-memory session still works.
  }
}

function expireSession(): void {
  const wasSignedIn = session !== null;
  saveSession(null);
  if (wasSignedIn) window.dispatchEvent(new Event("session-expired"));
}

const messages: Record<string, string> = {
  "Invalid credentials": "Неверный email или пароль.",
  "Bad credentials": "Неверный email или пароль.",
  "Validation failed": "Проверьте заполненные поля.",
  "Invalid request body": "Проверьте формат введённых данных.",
  "Invalid request parameter": "Проверьте параметры запроса.",
  "Data has changed. Reload it and retry the operation.":
    "Данные изменились. Обновите страницу и повторите действие.",
  "Operation conflicts with existing or related data":
    "Эти данные уже существуют или используются в других записях.",
  "User financial data has been deleted":
    "Финансовые данные профиля уже удалены.",
  "Category type cannot be changed": "Тип созданной категории изменить нельзя.",
  "Account balance can only be changed through transactions":
    "Баланс изменяется только через операции.",
  "Account currency cannot be changed":
    "Валюту созданного счёта изменить нельзя.",
  "Invalid timezone or date range": "Проверьте даты и часовой пояс.",
  "From and to are required; from must not be after to":
    "Начало периода должно быть не позже окончания.",
};

function validationMessage(field: string): string {
  switch (field) {
    case "email":
      return "Укажите корректный email длиной до 255 символов.";
    case "password":
      return "Пароль — от 8 символов и не более 72 байт UTF-8.";
    case "name":
      return "Укажите название допустимой длины.";
    case "surname":
      return "Укажите фамилию длиной до 100 символов.";
    case "amount":
      return "Укажите положительную сумму с двумя знаками после запятой.";
    case "balance":
      return "Укажите баланс с двумя знаками после запятой.";
    case "description":
      return "Описание должно содержать не более 500 символов.";
    case "accountId":
      return "Выберите счёт.";
    case "categoryId":
      return "Выберите категорию.";
    case "currency":
      return "Выберите валюту.";
    case "type":
      return "Выберите тип категории.";
    case "version":
      return "Данные изменились. Обновите список и повторите действие.";
    default:
      return "Проверьте значение поля.";
  }
}

function responseError(status: number, value: unknown): ApiError {
  const body =
    value && typeof value === "object" ? (value as ErrorResponse) : {};
  const errors = Object.fromEntries(
    Object.keys(body.errors ?? {}).map((field) => [
      field,
      validationMessage(field),
    ]),
  );
  const fallback: Record<number, string> = {
    400: "Проверьте заполненные поля и параметры запроса.",
    401: "Сессия завершена. Войдите в аккаунт снова.",
    403: "У вас нет доступа к этому действию.",
    404: "Запись не найдена. Возможно, она уже удалена.",
    409: "Данные изменились или конфликтуют с существующими записями. Обновите список.",
    429: "Слишком много запросов. Попробуйте немного позже.",
    502: "Сервис временно недоступен. Попробуйте позже.",
    503: "Сервис временно недоступен. Попробуйте позже.",
    504: "Сервис отвечает слишком долго. Попробуйте позже.",
  };
  return new ApiError(
    status,
    messages[body.message ?? ""] ??
      fallback[status] ??
      "Не удалось выполнить действие. Попробуйте позже.",
    errors,
  );
}

async function transport<T>(
  path: string,
  method: string,
  body?: unknown,
  accessToken?: string,
): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`/api${path}`, {
      method,
      headers: {
        Accept: "application/json",
        ...(body === undefined ? {} : { "Content-Type": "application/json" }),
        ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
      cache: "no-store",
      signal: AbortSignal.timeout(20_000),
    });
  } catch {
    throw new ApiError(
      0,
      "Не удалось связаться с сервером. Проверьте соединение. Если вы сохраняли данные, проверьте список перед повтором.",
    );
  }
  let text: string;
  try {
    text = await response.text();
  } catch {
    throw new ApiError(
      0,
      "Соединение прервалось. Если вы сохраняли данные, проверьте список перед повтором.",
    );
  }
  let value: unknown = text;
  if (text) {
    try {
      value = JSON.parse(text);
    } catch {
      /* Some API mutations return a plain-text acknowledgement. */
    }
  }
  if (!response.ok) throw responseError(response.status, value);
  return value as T;
}

async function refreshSession(): Promise<void> {
  if (refreshPromise) return refreshPromise;
  const refreshToken = session?.refreshToken;
  if (!refreshToken) {
    expireSession();
    throw new ApiError(401, "Сессия завершена. Войдите в аккаунт снова.");
  }
  const revision = sessionRevision;
  refreshPromise = (async () => {
    try {
      const pair = await transport<TokenPair>("/auth/refresh", "POST", {
        refreshToken,
      });
      if (!isTokenPair(pair))
        throw new ApiError(
          502,
          "Сервер вернул некорректный ответ авторизации.",
        );
      if (revision !== sessionRevision)
        throw new ApiError(401, "Сессия изменилась. Повторите вход.");
      saveSession(pair);
    } catch (error) {
      if (
        revision === sessionRevision &&
        error instanceof ApiError &&
        [400, 401, 403].includes(error.status)
      ) {
        expireSession();
      }
      throw error;
    } finally {
      refreshPromise = null;
    }
  })();
  return refreshPromise;
}

async function request<T>(
  path: string,
  method = "GET",
  body?: unknown,
): Promise<T> {
  const token = session?.token;
  if (!token) throw new ApiError(401, "Войдите в аккаунт, чтобы продолжить.");
  try {
    return await transport<T>(path, method, body, token);
  } catch (error) {
    if (!(error instanceof ApiError) || error.status !== 401) throw error;
    // A 401 rejects authorization before mutation. A lost response never triggers a replay.
    if (session?.token === token) await refreshSession();
    const renewedToken = session?.token;
    if (!renewedToken)
      throw new ApiError(401, "Сессия завершена. Войдите в аккаунт снова.");
    try {
      return await transport<T>(path, method, body, renewedToken);
    } catch (retryError) {
      if (
        retryError instanceof ApiError &&
        retryError.status === 401 &&
        session?.token === renewedToken
      )
        expireSession();
      throw retryError;
    }
  }
}

async function signIn(credentials: Credentials): Promise<void> {
  let pair: TokenPair;
  try {
    pair = await transport<TokenPair>("/auth/sign-in", "POST", credentials);
  } catch (error) {
    if (error instanceof ApiError && error.status === 401)
      throw new ApiError(401, "Неверный email или пароль.");
    throw error;
  }
  if (!isTokenPair(pair))
    throw new ApiError(502, "Сервер вернул некорректный ответ авторизации.");
  saveSession(pair);
}

export const api = {
  signIn,

  async register(input: RegisterInput): Promise<void> {
    await transport<User>("/users/registration", "POST", input);
    try {
      await signIn({ email: input.email, password: input.password });
    } catch (error) {
      throw new ApiError(
        error instanceof ApiError ? error.status : 0,
        "Аккаунт создан, но войти автоматически не удалось. Войдите с вашим email и паролем.",
      );
    }
  },

  async load(): Promise<DataSet> {
    const [user, accounts, categories, transactions] = await Promise.all([
      request<User>("/users/me"),
      request<Account[]>("/finance/accounts"),
      request<Category[]>("/finance/categories"),
      request<Transaction[]>("/finance/transactions"),
    ]);
    return { user, accounts, categories, transactions };
  },

  summary(
    accountId: number,
    from: string,
    to: string,
    timezone: string,
  ): Promise<Summary> {
    const query = new URLSearchParams({
      accountId: String(accountId),
      from,
      to,
      timezone,
    });
    return request<Summary>(`/analytics/summary/range?${query}`);
  },

  createAccount(input: AccountInput): Promise<Account> {
    return request<Account>("/finance/accounts", "POST", input);
  },

  updateAccount(account: Account): Promise<Account> {
    const { name, balance, currency, version } = account;
    return request<Account>(`/finance/accounts/${account.id}`, "PUT", {
      name,
      balance,
      currency,
      version,
    });
  },

  deleteAccount(account: Account): Promise<void> {
    return request<void>(
      `/finance/accounts/${account.id}?version=${account.version}`,
      "DELETE",
    );
  },

  createCategory(input: CategoryInput): Promise<Category> {
    return request<Category>("/finance/categories", "POST", input);
  },

  updateCategory(category: Category): Promise<void> {
    return request<void>(`/finance/categories/${category.id}`, "PUT", {
      name: category.name,
      type: category.type,
    });
  },

  deleteCategory(id: number): Promise<void> {
    return request<void>(`/finance/categories/${id}`, "DELETE");
  },

  createTransaction(input: TransactionInput): Promise<Transaction> {
    return request<Transaction>("/finance/transactions", "POST", input);
  },

  updateTransaction(transaction: Transaction): Promise<Transaction> {
    const { accountId, categoryId, amount, description, version } = transaction;
    return request<Transaction>(
      `/finance/transactions/${transaction.id}`,
      "PUT",
      { accountId, categoryId, amount, description, version },
    );
  },

  deleteTransaction(transaction: Transaction): Promise<void> {
    return request<void>(
      `/finance/transactions/${transaction.id}?version=${transaction.version}`,
      "DELETE",
    );
  },

  updateUser(user: User): Promise<void> {
    return request<void>(`/users/${user.id}`, "PUT", {
      name: user.name,
      surname: user.surname,
      email: user.email,
    });
  },

  async deleteUser(id: number): Promise<void> {
    await request<void>(`/users/${id}`, "DELETE");
    saveSession(null);
  },

  logout(): void {
    saveSession(null);
  },

  hasSession(): boolean {
    return session !== null;
  },
};
