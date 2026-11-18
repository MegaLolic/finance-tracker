import {
  lazy,
  Suspense,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import type { FormEvent, ReactNode } from "react";
import {
  ArrowDownLeft,
  ArrowUpRight,
  ArrowRight,
  ArrowLeftRight,
  BarChart3,
  CalendarDays,
  Check,
  ChevronLeft,
  ChevronRight,
  CircleAlert,
  Download,
  Eye,
  EyeOff,
  LayoutDashboard,
  Loader2,
  LogOut,
  Pencil,
  Plus,
  RefreshCw,
  Search,
  Settings,
  ShieldCheck,
  Smartphone,
  Sparkles,
  Tags,
  Trash2,
  TrendingUp,
  Wallet,
  X,
} from "lucide-react";
import type {
  Account,
  Category,
  CategoryType,
  Currency,
  DataSet,
  Summary,
  Transaction,
} from "./types";
import { api } from "./api";
import { createDemo } from "./demo";
import {
  filterTransactions,
  formatMoney,
  localDate,
  rangeFor,
  reportTotals,
} from "./lib/reporting";

const ReportCharts = lazy(() => import("./components/ReportCharts"));

type Page =
  | "overview"
  | "transactions"
  | "reports"
  | "accounts"
  | "categories"
  | "settings";
type Period = "DAY" | "WEEK" | "MONTH" | "YEAR" | "CUSTOM";
type EditorState =
  | { kind: "transaction"; item?: Transaction }
  | { kind: "account"; item?: Account }
  | { kind: "category"; item?: Category };
type Confirmation = {
  title: string;
  description: string;
  action: () => Promise<void>;
  danger?: boolean;
  phrase?: string;
};
type InstallPrompt = Event & {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: string }>;
};
const navigation = [
  { id: "overview", label: "Обзор", icon: LayoutDashboard },
  { id: "transactions", label: "Операции", icon: ArrowLeftRight },
  { id: "reports", label: "Отчёты", icon: BarChart3 },
  { id: "accounts", label: "Счета", icon: Wallet },
  { id: "categories", label: "Категории", icon: Tags },
] as const;
const errorText = (error: unknown) =>
  error instanceof Error
    ? error.message
    : "Не удалось выполнить действие. Попробуйте ещё раз.";
const initials = (name: string, surname = "") =>
  (name[0] || "") + (surname[0] || "");
const cents = (amount: number) => Math.round(amount * 100);
const signedAmount = (transaction: Transaction, categories: Category[]) =>
  (categories.find((c) => c.id === transaction.categoryId)?.type === "INCOME"
    ? 1
    : -1) * cents(transaction.amount);

function Brand({ compact = false }: { compact?: boolean }) {
  return (
    <div className="brand">
      <span className="brand-symbol">
        <i />
        <i />
        <i />
      </span>
      {!compact && (
        <span>
          финанс<span className="brand-dot">.</span>
        </span>
      )}
    </div>
  );
}

function Modal({
  title,
  subtitle,
  children,
  close,
  busy = false,
}: {
  title: string;
  subtitle?: string;
  children: ReactNode;
  close: () => void;
  busy?: boolean;
}) {
  const panel = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const previous = document.activeElement as HTMLElement;
    const oldOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    panel.current?.querySelector<HTMLElement>("input,select,button")?.focus();
    return () => {
      document.body.style.overflow = oldOverflow;
      previous?.focus();
    };
  }, []);
  return (
    <div
      className="modal-backdrop"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && !busy) close();
      }}
    >
      <div
        className="modal"
        ref={panel}
        role="dialog"
        aria-modal="true"
        aria-labelledby="modal-title"
        onKeyDown={(event) => {
          if (event.key === "Escape" && !busy) close();
          if (event.key === "Tab") {
            const elements = Array.from(
              panel.current?.querySelectorAll<HTMLElement>(
                "button:not(:disabled),input:not(:disabled),select:not(:disabled),textarea:not(:disabled),a[href]",
              ) || [],
            );
            if (!elements.length) return;
            if (event.shiftKey && document.activeElement === elements[0]) {
              event.preventDefault();
              elements.at(-1)?.focus();
            } else if (
              !event.shiftKey &&
              document.activeElement === elements.at(-1)
            ) {
              event.preventDefault();
              elements[0]?.focus();
            }
          }
        }}
      >
        <div className="modal-heading">
          <div>
            <h2 id="modal-title">{title}</h2>
            {subtitle && <p>{subtitle}</p>}
          </div>
          <button
            className="icon-button"
            aria-label="Закрыть"
            disabled={busy}
            onClick={close}
          >
            <X size={20} />
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}

function Auth({ onAuth, onDemo }: { onAuth: () => void; onDemo: () => void }) {
  const [register, setRegister] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [visible, setVisible] = useState(false);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setBusy(true);
    const form = new FormData(event.currentTarget);
    const password = String(form.get("password"));
    try {
      if (
        register &&
        (password.length < 8 || new TextEncoder().encode(password).length > 72)
      )
        throw new Error(
          "Пароль должен содержать от 8 символов и не больше 72 байт UTF-8.",
        );
      const credentials = { email: String(form.get("email")).trim(), password };
      if (register)
        await api.register({
          ...credentials,
          name: String(form.get("name")).trim(),
          surname: String(form.get("surname")).trim(),
        });
      else await api.signIn(credentials);
      onAuth();
    } catch (cause) {
      setError(errorText(cause));
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="auth-page">
      <section className="auth-story">
        <Brand />
        <div className="auth-story-content">
          <span className="eyebrow">
            <span className="live-dot" /> ВАШИ ДЕНЬГИ. ВАША ЯСНОСТЬ.
          </span>
          <h1>
            Меньше забот.
            <br />
            Больше <em>свободы.</em>
          </h1>
          <p>
            Понимайте, куда уходят деньги.
            <br />
            Находите баланс. Планируйте своё завтра.
          </p>
          <div className="auth-art" aria-hidden="true">
            <div className="art-card">
              <span>
                Всё под контролем <Check size={15} />
              </span>
              <strong>Финансовая ясность</strong>
              <svg viewBox="0 0 380 130">
                <defs>
                  <linearGradient id="artFill" x1="0" y1="0" x2="0" y2="1">
                    <stop stopColor="#b4aaff" stopOpacity=".35" />
                    <stop offset="1" stopColor="#b4aaff" stopOpacity="0" />
                  </linearGradient>
                </defs>
                <path
                  d="M0 110 C30 110 35 65 65 76 S105 110 135 60 S180 95 210 50 S260 80 290 26 S340 53 380 10 L380 130 L0 130Z"
                  fill="url(#artFill)"
                />
                <path
                  d="M0 110 C30 110 35 65 65 76 S105 110 135 60 S180 95 210 50 S260 80 290 26 S340 53 380 10"
                  fill="none"
                  stroke="#c7bfff"
                  strokeWidth="3"
                />
              </svg>
              <div className="art-bars">
                {[32, 55, 43, 76, 61, 88, 69, 100, 83, 119, 98, 131].map(
                  (height, index) => (
                    <i key={index} style={{ height }} />
                  ),
                )}
              </div>
            </div>
            <span className="art-badge">
              <ShieldCheck size={20} /> Только ваши финансы
            </span>
          </div>
        </div>
        <small>Личный учёт. Понятные отчёты. В одном месте.</small>
      </section>
      <section className="auth-form-side">
        <div className="auth-mobile-brand">
          <Brand />
        </div>
        <div className="auth-form-wrap">
          <span className="pill">
            <Sparkles size={14} /> НАЧНИТЕ С ЯСНОСТИ
          </span>
          <h2>{register ? "Давайте знакомиться" : "С возвращением"}</h2>
          <p>
            {register
              ? "Создайте аккаунт и добавьте свой первый счёт."
              : "Ваши финансы ждут. Войдите в свой аккаунт."}
          </p>
          <form onSubmit={submit} className="form-stack">
            {register && (
              <div className="form-two">
                <label>
                  Имя
                  <input
                    name="name"
                    autoComplete="given-name"
                    required
                    maxLength={100}
                    placeholder="Ваше имя"
                  />
                </label>
                <label>
                  Фамилия
                  <input
                    name="surname"
                    autoComplete="family-name"
                    required
                    maxLength={100}
                    placeholder="Ваша фамилия"
                  />
                </label>
              </div>
            )}
            <label>
              Электронная почта
              <input
                name="email"
                type="email"
                autoComplete="email"
                required
                maxLength={255}
                placeholder="you@example.com"
              />
            </label>
            <label>
              Пароль
              <div className="password-field">
                <input
                  name="password"
                  type={visible ? "text" : "password"}
                  autoComplete={register ? "new-password" : "current-password"}
                  required
                  minLength={register ? 8 : undefined}
                  placeholder={
                    register ? "Не менее 8 символов" : "Введите пароль"
                  }
                />
                <button
                  type="button"
                  aria-label={visible ? "Скрыть пароль" : "Показать пароль"}
                  onClick={() => setVisible(!visible)}
                >
                  {visible ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
            </label>
            {error && (
              <div className="form-error" role="alert">
                <CircleAlert size={17} />
                {error}
              </div>
            )}
            <button className="button primary full" disabled={busy}>
              {busy ? (
                <Loader2 className="spin" size={18} />
              ) : (
                <>
                  {register ? "Создать аккаунт" : "Войти"}
                  <ArrowRight size={18} />
                </>
              )}
            </button>
          </form>
          <p className="auth-switch">
            {register ? "Уже есть аккаунт?" : "Ещё нет аккаунта?"}{" "}
            <button
              disabled={busy}
              onClick={() => {
                setRegister(!register);
                setError("");
              }}
            >
              {register ? "Войти" : "Зарегистрироваться"}
            </button>
          </p>
          <div className="auth-divider">
            <span>или сначала познакомьтесь</span>
          </div>
          <button className="button secondary full" onClick={onDemo}>
            <BarChart3 size={18} />
            Посмотреть демо
          </button>
          <small className="auth-note">
            Без регистрации. Демо не меняет ваши данные.
          </small>
        </div>
        <span className="auth-footer">
          <Smartphone size={15} /> Комфортно на компьютере и телефоне
        </span>
      </section>
    </div>
  );
}

function Editor({
  state,
  data,
  accountId,
  close,
  save,
}: {
  state: EditorState;
  data: DataSet;
  accountId: number;
  close: () => void;
  save: (form: FormData) => Promise<void>;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const initialType =
    state.kind === "transaction"
      ? data.categories.find((c) => c.id === state.item?.categoryId)?.type ||
        "EXPENSE"
      : state.kind === "category"
        ? state.item?.type || "EXPENSE"
        : "EXPENSE";
  const [type, setType] = useState<CategoryType>(initialType);
  const [chosenCategory, setChosenCategory] = useState(
    state.kind === "transaction" ? String(state.item?.categoryId || "") : "",
  );
  const categoryOptions = data.categories.filter((c) => c.type === type);
  const titles = {
    transaction: "операцию",
    account: "счёт",
    category: "категорию",
  };
  const title = `${state.item ? "Изменить" : "Добавить"} ${titles[state.kind]}`;
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setBusy(true);
    try {
      await save(new FormData(event.currentTarget));
      close();
    } catch (cause) {
      setError(errorText(cause));
    } finally {
      setBusy(false);
    }
  }
  return (
    <Modal
      title={title}
      subtitle={
        state.kind === "transaction"
          ? "Небольшая запись — больше порядка в финансах."
          : undefined
      }
      close={close}
      busy={busy}
    >
      <form onSubmit={submit} className="form-stack">
        {state.kind !== "account" && (
          <div className="type-switch">
            {(["EXPENSE", "INCOME"] as CategoryType[]).map((value) => (
              <button
                key={value}
                type="button"
                disabled={state.kind === "category" && !!state.item}
                className={type === value ? "active" : ""}
                onClick={() => {
                  setType(value);
                  setChosenCategory("");
                }}
              >
                {value === "INCOME" ? (
                  <ArrowDownLeft size={17} />
                ) : (
                  <ArrowUpRight size={17} />
                )}
                {value === "INCOME" ? "Доход" : "Расход"}
              </button>
            ))}
          </div>
        )}
        {state.kind === "transaction" ? (
          <>
            <label>
              Сумма
              <input
                name="amount"
                inputMode="decimal"
                placeholder="0,00"
                defaultValue={state.item?.amount}
                required
                maxLength={18}
              />
            </label>
            <div className="form-two">
              <label>
                Счёт
                <select
                  name="accountId"
                  defaultValue={
                    state.item?.accountId || accountId || data.accounts[0]?.id
                  }
                  required
                >
                  {data.accounts.map((a) => (
                    <option value={a.id} key={a.id}>
                      {a.name} · {a.currency}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Категория
                <select
                  name="categoryId"
                  value={chosenCategory}
                  onChange={(e) => setChosenCategory(e.target.value)}
                  required
                >
                  <option value="" disabled>
                    Выберите категорию
                  </option>
                  {categoryOptions.map((c) => (
                    <option value={c.id} key={c.id}>
                      {c.name}
                    </option>
                  ))}
                </select>
              </label>
            </div>
            {!categoryOptions.length && (
              <p className="field-hint">
                Сначала добавьте категорию{" "}
                {type === "EXPENSE" ? "расходов" : "доходов"} в разделе
                «Категории».
              </p>
            )}
            <label>
              Комментарий <span className="optional">необязательно</span>
              <textarea
                name="description"
                maxLength={500}
                placeholder="На что потратили или откуда получили?"
                defaultValue={state.item?.description || ""}
                rows={3}
              />
            </label>
            <span className="field-hint">
              Дата операции устанавливается при её создании.
            </span>
          </>
        ) : (
          <>
            <label>
              Название
              <input
                name="name"
                required
                maxLength={50}
                defaultValue={state.item?.name}
                placeholder={
                  state.kind === "account"
                    ? "Например, Основной счёт"
                    : "Например, Продукты"
                }
              />
            </label>
            {state.kind === "account" &&
              (state.item ? (
                <div className="form-info">
                  <Wallet size={20} />
                  <span>
                    Баланс{" "}
                    {formatMoney(state.item.balance, state.item.currency)}
                    <small>
                      Баланс меняется при добавлении операций. Валюта счёта
                      сохраняется.
                    </small>
                  </span>
                </div>
              ) : (
                <div className="form-two">
                  <label>
                    Начальный баланс
                    <input
                      name="balance"
                      inputMode="decimal"
                      defaultValue="0"
                      required
                      maxLength={18}
                    />
                  </label>
                  <label>
                    Валюта
                    <select name="currency" defaultValue="BYN">
                      {["BYN", "USD", "EUR", "RUB"].map((c) => (
                        <option key={c}>{c}</option>
                      ))}
                    </select>
                  </label>
                </div>
              ))}
            {state.kind === "category" && (
              <input type="hidden" name="type" value={type} />
            )}
          </>
        )}
        {error && (
          <div className="form-error" role="alert">
            <CircleAlert size={17} />
            {error}
          </div>
        )}
        <div className="modal-actions">
          <button
            type="button"
            className="button secondary"
            disabled={busy}
            onClick={close}
          >
            Отмена
          </button>
          <button
            className="button primary"
            disabled={
              busy || (state.kind === "transaction" && !categoryOptions.length)
            }
          >
            {busy ? (
              <Loader2 className="spin" size={18} />
            ) : (
              <>
                <Check size={17} />
                {state.item ? "Сохранить" : "Добавить"}
              </>
            )}
          </button>
        </div>
      </form>
    </Modal>
  );
}

function ConfirmDialog({
  confirmation,
  close,
}: {
  confirmation: Confirmation;
  close: () => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [phrase, setPhrase] = useState("");
  return (
    <Modal title={confirmation.title} close={close} busy={busy}>
      <p className="confirm-copy">{confirmation.description}</p>
      {confirmation.phrase && (
        <label className="form-stack">
          Для подтверждения введите «{confirmation.phrase}»
          <input
            value={phrase}
            onChange={(e) => setPhrase(e.target.value)}
            autoComplete="off"
          />
        </label>
      )}
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
      <div className="modal-actions">
        <button className="button secondary" disabled={busy} onClick={close}>
          Отмена
        </button>
        <button
          className={`button ${confirmation.danger ? "danger" : "primary"}`}
          disabled={
            busy || (!!confirmation.phrase && phrase !== confirmation.phrase)
          }
          onClick={async () => {
            setBusy(true);
            try {
              await confirmation.action();
              close();
            } catch (cause) {
              setError(errorText(cause));
            } finally {
              setBusy(false);
            }
          }}
        >
          {busy ? (
            <Loader2 className="spin" size={17} />
          ) : confirmation.danger ? (
            "Удалить"
          ) : (
            "Подтвердить"
          )}
        </button>
      </div>
    </Modal>
  );
}

export default function App() {
  const [authenticated, setAuthenticated] = useState(api.hasSession());
  const [demo, setDemo] = useState(false);
  const [data, setData] = useState<DataSet | null>(null);
  const [page, setPage] = useState<Page>("overview");
  const [accountId, setAccountId] = useState(0);
  const [period, setPeriod] = useState<Period>("MONTH");
  const [anchor, setAnchor] = useState(localDate());
  const [customFrom, setCustomFrom] = useState(
    localDate(new Date(new Date().getFullYear(), new Date().getMonth(), 1)),
  );
  const [customTo, setCustomTo] = useState(localDate());
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState("");
  const [summary, setSummary] = useState<Summary | null>(null);
  const [sync, setSync] = useState<"loading" | "ready" | "pending" | "error">(
    "loading",
  );
  const [editor, setEditor] = useState<EditorState | null>(null);
  const [confirmation, setConfirmation] = useState<Confirmation | null>(null);
  const [notice, setNotice] = useState("");
  const [query, setQuery] = useState("");
  const [typeFilter, setTypeFilter] = useState("ALL");
  const [limit, setLimit] = useState(15);
  const [online, setOnline] = useState(navigator.onLine);
  const [install, setInstall] = useState<InstallPrompt | null>(null);
  const [profileBusy, setProfileBusy] = useState(false);
  const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC";
  const range = useMemo(() => {
    try {
      return rangeFor(period, anchor, customFrom, customTo);
    } catch {
      return null;
    }
  }, [period, anchor, customFrom, customTo]);
  const activeAccount =
    data?.accounts.find((a) => a.id === accountId) || data?.accounts[0];
  const selectedTransactions = useMemo(
    () =>
      data && activeAccount && range
        ? filterTransactions(
            data.transactions,
            activeAccount.id,
            range.from,
            range.to,
          )
        : [],
    [data, activeAccount, range],
  );
  const totals = useMemo(
    () => reportTotals(selectedTransactions, data?.categories || []),
    [selectedTransactions, data?.categories],
  );
  const figures = demo ? totals : summary || totals;
  const currency = activeAccount?.currency || "BYN";
  const displayed = useMemo(
    () =>
      selectedTransactions
        .filter((t) => {
          const category = data?.categories.find((c) => c.id === t.categoryId);
          return (
            (typeFilter === "ALL" || category?.type === typeFilter) &&
            `${t.description || ""} ${category?.name || ""} ${t.amount}`
              .toLowerCase()
              .includes(query.toLowerCase())
          );
        })
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.id - a.id),
    [selectedTransactions, data, query, typeFilter],
  );

  const notify = (message: string) => setNotice(message);
  const refresh = useCallback(async () => {
    if (demo) return;
    setLoading(true);
    setLoadError("");
    try {
      const fresh = await api.load();
      setData(fresh);
      setAccountId((current) =>
        fresh.accounts.some((a) => a.id === current)
          ? current
          : fresh.accounts[0]?.id || 0,
      );
    } catch (cause) {
      setLoadError(errorText(cause));
    } finally {
      setLoading(false);
    }
  }, [demo]);
  useEffect(() => {
    if (authenticated && !demo) void refresh();
  }, [authenticated, demo, refresh]);
  useEffect(() => {
    const expired = () => {
      setAuthenticated(false);
      setData(null);
      setEditor(null);
      setConfirmation(null);
      notify("Сессия завершена. Войдите ещё раз.");
    };
    const status = () => setOnline(navigator.onLine);
    const prompt = (event: Event) => {
      event.preventDefault();
      setInstall(event as InstallPrompt);
    };
    window.addEventListener("session-expired", expired);
    window.addEventListener("online", status);
    window.addEventListener("offline", status);
    window.addEventListener("beforeinstallprompt", prompt);
    return () => {
      window.removeEventListener("session-expired", expired);
      window.removeEventListener("online", status);
      window.removeEventListener("offline", status);
      window.removeEventListener("beforeinstallprompt", prompt);
    };
  }, []);
  useEffect(() => {
    if (!notice) return;
    const timer = setTimeout(() => setNotice(""), 6500);
    return () => clearTimeout(timer);
  }, [notice]);
  useEffect(() => {
    setLimit(15);
  }, [
    page,
    query,
    typeFilter,
    accountId,
    period,
    anchor,
    customFrom,
    customTo,
  ]);
  useEffect(() => {
    if (demo || !activeAccount || !range) {
      setSummary(null);
      return;
    }
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout>;
    const started = Date.now();
    setSummary(null);
    setSync("loading");
    const poll = async () => {
      try {
        const result = await api.summary(
          activeAccount.id,
          range.from,
          range.to,
          timezone,
        );
        if (cancelled) return;
        setSummary(result);
        const matches =
          result.transactionCount === totals.transactionCount &&
          cents(result.totalIncome) === cents(totals.totalIncome) &&
          cents(result.totalExpense) === cents(totals.totalExpense);
        setSync(matches ? "ready" : "pending");
        if (!matches && Date.now() - started < 25000)
          timer = setTimeout(poll, 2000);
      } catch {
        if (!cancelled) setSync("error");
      }
    };
    void poll();
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [demo, activeAccount?.id, range?.from, range?.to, totals, timezone]);

  function enterDemo() {
    const sample = createDemo();
    setDemo(true);
    setData(sample);
    setAccountId(sample.accounts[0].id);
    setAuthenticated(true);
    setPage("overview");
  }
  function logout() {
    api.logout();
    setAuthenticated(false);
    setDemo(false);
    setData(null);
    setSummary(null);
    setEditor(null);
    setConfirmation(null);
    setPage("overview");
  }
  function editOperation() {
    if (!data?.accounts.length) {
      setPage("accounts");
      notify("Добавьте счёт, чтобы записывать операции.");
      setEditor({ kind: "account" });
      return;
    }
    if (!data.categories.length) {
      setPage("categories");
      notify("Добавьте первую категорию.");
      setEditor({ kind: "category" });
      return;
    }
    setEditor({ kind: "transaction" });
  }
  function monetary(value: FormDataEntryValue | null, allowNegative = false) {
    const text = String(value || "")
      .trim()
      .replace(",", ".");
    if (!/^-?\d+(\.\d{1,2})?$/.test(text))
      throw new Error(
        "Введите сумму с точностью до двух знаков после запятой.",
      );
    const amount = Number(text);
    if (
      !Number.isFinite(amount) ||
      Math.abs(amount) > 1_000_000_000_000 ||
      (!allowNegative && amount <= 0)
    )
      throw new Error(
        allowNegative
          ? "Сумма должна быть в пределах ±1 трлн."
          : "Сумма должна быть больше нуля и не больше 1 трлн.",
      );
    return amount;
  }
  async function saveEditor(form: FormData) {
    if (!editor || !data) return;
    const nextId = Date.now();
    if (editor.kind === "account") {
      const name = String(form.get("name")).trim();
      if (!name) throw new Error("Введите название счёта.");
      if (editor.item) {
        if (demo)
          setData({
            ...data,
            accounts: data.accounts.map((a) =>
              a.id === editor.item?.id
                ? { ...a, name, version: a.version + 1 }
                : a,
            ),
          });
        else {
          const latest = (await api.load()).accounts.find(
            (a) => a.id === editor.item!.id,
          );
          if (!latest) throw new Error("Счёт уже удалён. Обновите страницу.");
          await api.updateAccount({ ...latest, name });
        }
      } else {
        const input = {
          name,
          balance: monetary(form.get("balance"), true),
          currency: String(form.get("currency")) as Currency,
        };
        if (demo) {
          setData({
            ...data,
            accounts: [...data.accounts, { ...input, id: nextId, version: 0 }],
          });
          setAccountId(nextId);
        } else {
          const account = await api.createAccount(input);
          setAccountId(account.id);
        }
      }
    } else if (editor.kind === "category") {
      const input = {
        name: String(form.get("name")).trim(),
        type: String(form.get("type")) as CategoryType,
      };
      if (!input.name) throw new Error("Введите название категории.");
      if (demo) {
        if (
          data.categories.some(
            (c) =>
              c.id !== editor.item?.id &&
              c.name.toLowerCase() === input.name.toLowerCase(),
          )
        )
          throw new Error("Категория с таким названием уже существует.");
        setData({
          ...data,
          categories: editor.item
            ? data.categories.map((c) =>
                c.id === editor.item?.id ? { ...c, ...input } : c,
              )
            : [...data.categories, { ...input, id: nextId }],
        });
      } else if (editor.item)
        await api.updateCategory({ ...editor.item, ...input });
      else await api.createCategory(input);
    } else {
      const input = {
        accountId: Number(form.get("accountId")),
        categoryId: Number(form.get("categoryId")),
        amount: monetary(form.get("amount")),
        description: String(form.get("description") || "").trim(),
      };
      if (demo) {
        const transaction: Transaction = {
          ...input,
          id: editor.item?.id || nextId,
          version: (editor.item?.version ?? -1) + 1,
          createdAt: editor.item?.createdAt || new Date().toISOString(),
        };
        const old = editor.item;
        setData({
          ...data,
          accounts: data.accounts.map((a) => ({
            ...a,
            balance:
              (cents(a.balance) -
                (old?.accountId === a.id
                  ? signedAmount(old, data.categories)
                  : 0) +
                (transaction.accountId === a.id
                  ? signedAmount(transaction, data.categories)
                  : 0)) /
              100,
          })),
          transactions: old
            ? data.transactions.map((t) => (t.id === old.id ? transaction : t))
            : [...data.transactions, transaction],
        });
      } else if (editor.item)
        await api.updateTransaction({ ...editor.item, ...input });
      else await api.createTransaction(input);
    }
    if (!demo) await refresh();
    notify(editor.item ? "Изменения сохранены" : "Готово! Запись добавлена");
  }
  function removeTransaction(transaction: Transaction) {
    setConfirmation({
      title: "Удалить операцию?",
      description:
        "Сумма операции будет возвращена на счёт. Отчёт обновится после удаления.",
      danger: true,
      action: async () => {
        if (demo && data)
          setData({
            ...data,
            transactions: data.transactions.filter(
              (t) => t.id !== transaction.id,
            ),
            accounts: data.accounts.map((a) =>
              a.id === transaction.accountId
                ? {
                    ...a,
                    balance:
                      (cents(a.balance) -
                        signedAmount(transaction, data.categories)) /
                      100,
                  }
                : a,
            ),
          });
        else {
          await api.deleteTransaction(transaction);
          await refresh();
        }
        notify("Операция удалена");
      },
    });
  }
  function removeAccount(account: Account) {
    setConfirmation({
      title: `Удалить счёт «${account.name}»?`,
      description:
        "Счёт и все связанные с ним операции будут удалены. Это действие нельзя отменить.",
      danger: true,
      action: async () => {
        if (demo && data) {
          const remaining = data.accounts.filter((a) => a.id !== account.id);
          setData({
            ...data,
            accounts: remaining,
            transactions: data.transactions.filter(
              (t) => t.accountId !== account.id,
            ),
          });
          if (accountId === account.id) setAccountId(remaining[0]?.id || 0);
        } else {
          const fresh = (await api.load()).accounts.find(
            (a) => a.id === account.id,
          );
          if (fresh) await api.deleteAccount(fresh);
          await refresh();
        }
        notify("Счёт удалён");
      },
    });
  }
  function removeCategory(category: Category) {
    setConfirmation({
      title: `Удалить категорию «${category.name}»?`,
      description:
        "Можно удалить категорию, у которой нет операций. Сначала перенесите или удалите связанные операции.",
      danger: true,
      action: async () => {
        if (demo && data) {
          if (data.transactions.some((t) => t.categoryId === category.id))
            throw new Error("Категория используется в операциях.");
          setData({
            ...data,
            categories: data.categories.filter((c) => c.id !== category.id),
          });
        } else {
          await api.deleteCategory(category.id);
          await refresh();
        }
        notify("Категория удалена");
      },
    });
  }
  function exportCsv() {
    if (!data || !activeAccount || !range) return;
    const safe = (value: unknown) => {
      let text = String(value ?? "");
      if (/^[=+@\-\t\r]/.test(text)) text = `'${text}`;
      return `"${text.replaceAll('"', '""')}"`;
    };
    const rows = [
      ["Дата", "Категория", "Тип", "Сумма", "Валюта", "Комментарий"],
      ...displayed.map((t) => {
        const category = data.categories.find((c) => c.id === t.categoryId);
        return [
          localDate(new Date(t.createdAt)),
          category?.name || "Без категории",
          category?.type === "INCOME" ? "Доход" : "Расход",
          t.amount.toFixed(2),
          currency,
          t.description || "",
        ];
      }),
    ];
    const url = URL.createObjectURL(
      new Blob(
        ["\uFEFF" + rows.map((row) => row.map(safe).join(";")).join("\r\n")],
        { type: "text/csv;charset=utf-8" },
      ),
    );
    const link = document.createElement("a");
    link.href = url;
    link.download = `Финанс_${range.from}_${range.to}.csv`;
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    notify("Выгрузка CSV готова");
  }
  function shiftPeriod(direction: number) {
    const date = new Date(`${anchor}T12:00:00`);
    if (period === "YEAR") date.setFullYear(date.getFullYear() + direction);
    else if (period === "MONTH") {
      date.setDate(1);
      date.setMonth(date.getMonth() + direction);
    } else
      date.setDate(date.getDate() + direction * (period === "WEEK" ? 7 : 1));
    setAnchor(localDate(date));
  }
  const transactionRows = (rows: Transaction[]) => (
    <div className="transaction-list">
      {rows.map((transaction) => {
        const category = data!.categories.find(
          (c) => c.id === transaction.categoryId,
        );
        const income = category?.type === "INCOME";
        return (
          <div className="transaction-row" key={transaction.id}>
            <span
              className={`transaction-icon ${income ? "income" : "expense"}`}
            >
              {income ? (
                <ArrowDownLeft size={19} />
              ) : (
                <ArrowUpRight size={19} />
              )}
            </span>
            <div className="transaction-detail">
              <strong>
                {transaction.description || category?.name || "Операция"}
              </strong>
              <span>
                {category?.name || "Категория удалена"}
                <i>·</i>
                {new Date(transaction.createdAt).toLocaleDateString("ru-RU", {
                  day: "numeric",
                  month: "short",
                })}
              </span>
            </div>
            <div className={`transaction-amount ${income ? "positive" : ""}`}>
              {income ? "+" : "−"}
              {formatMoney(transaction.amount, currency)}
              <small>{income ? "Доход" : "Расход"}</small>
            </div>
            <div className="row-actions">
              <button
                className="icon-button"
                aria-label="Изменить операцию"
                onClick={() =>
                  setEditor({ kind: "transaction", item: transaction })
                }
              >
                <Pencil size={15} />
              </button>
              <button
                className="icon-button danger-icon"
                aria-label="Удалить операцию"
                onClick={() => removeTransaction(transaction)}
              >
                <Trash2 size={15} />
              </button>
            </div>
          </div>
        );
      })}
    </div>
  );

  if (!authenticated)
    return (
      <>
        <Auth
          onAuth={() => {
            setDemo(false);
            setAuthenticated(true);
          }}
          onDemo={enterDemo}
        />
        {notice && (
          <div className="toast" role="status">
            <Check size={18} />
            {notice}
          </div>
        )}
      </>
    );
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <Brand />
        <div className="workspace-label">ЛИЧНЫЙ КАБИНЕТ</div>
        <nav aria-label="Основная навигация">
          {navigation.map((item) => (
            <button
              key={item.id}
              className={page === item.id ? "nav-item active" : "nav-item"}
              onClick={() => setPage(item.id)}
            >
              <item.icon size={20} />
              {item.label}
              {page === item.id && <span className="nav-indicator" />}
            </button>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <div className="sidebar-note">
            <span className="note-orbit">
              <TrendingUp size={22} />
            </span>
            <strong>
              Маленькие шаги.
              <br />
              Большие возможности.
            </strong>
            <p>Понимание своих финансов начинается с одной записи.</p>
          </div>
          {install && (
            <button
              className="nav-item"
              onClick={async () => {
                await install.prompt();
                setInstall(null);
              }}
            >
              <Smartphone size={19} />
              Установить приложение
            </button>
          )}
          <button
            className={page === "settings" ? "nav-item active" : "nav-item"}
            onClick={() => setPage("settings")}
          >
            <Settings size={19} />
            Настройки
          </button>
          <div className="sidebar-user">
            <span className="avatar">
              {data ? initials(data.user.name, data.user.surname) : "…"}
            </span>
            <div>
              <strong>{data?.user.name || "Загрузка…"}</strong>
              <small>
                {demo ? "Демонстрационный аккаунт" : "Личный аккаунт"}
              </small>
            </div>
            <button className="icon-button" aria-label="Выйти" onClick={logout}>
              <LogOut size={17} />
            </button>
          </div>
        </div>
      </aside>
      <main className="main-content">
        <header className="topbar">
          <div className="breadcrumbs">
            Личный кабинет <ChevronRight size={13} />
            <strong>
              {navigation.find((n) => n.id === page)?.label || "Настройки"}
            </strong>
          </div>
          <div className="topbar-right">
            {demo && (
              <span className="demo-badge">
                <span />
                Демо · пример данных
              </span>
            )}
            <span className="today">
              {new Date().toLocaleDateString("ru-RU", {
                day: "numeric",
                month: "long",
                year: "numeric",
              })}
            </span>
            <button
              className="avatar mobile-profile"
              aria-label="Настройки профиля"
              onClick={() => setPage("settings")}
            >
              {data ? initials(data.user.name) : "Ф"}
            </button>
          </div>
        </header>
        {!online && (
          <div className="banner warning">
            <CircleAlert size={17} />
            Нет подключения к интернету. Для сохранения операций нужна сеть.
          </div>
        )}
        {demo && (
          <div className="demo-strip">
            <Sparkles size={16} />
            <span>
              Это демоверсия. Попробуйте добавить операцию — ваши реальные
              данные не изменятся.
            </span>
            <button onClick={logout}>
              Создать свой аккаунт
              <ArrowRight size={14} />
            </button>
          </div>
        )}
        <div className="content-wrap">
          <div className="page-heading">
            <div>
              <div className="eyebrow">
                {page === "overview"
                  ? "ВАША ФИНАНСОВАЯ КАРТИНА"
                  : page === "reports"
                    ? "ЦИФРЫ, КОТОРЫЕ ГОВОРЯТ"
                    : "ВСЁ ПОД КОНТРОЛЕМ"}
              </div>
              <h1>
                {page === "overview"
                  ? `Привет, ${data?.user.name || "друг"}!`
                  : navigation.find((n) => n.id === page)?.label || "Настройки"}
                {page === "overview" && <span className="greeting-dot">✦</span>}
              </h1>
              <p>
                {
                  {
                    overview:
                      "Чуть больше ясности в ваших ежедневных финансах.",
                    transactions:
                      "Каждая операция — часть вашей финансовой истории.",
                    reports:
                      "Наглядно о том, откуда приходят и куда уходят деньги.",
                    accounts: "У каждого счёта своя роль. Найдите свой баланс.",
                    categories: "Простой порядок в ваших доходах и расходах.",
                    settings: "Профиль и настройки вашего пространства.",
                  }[page]
                }
              </p>
            </div>
            <div className="heading-actions">
              {["overview", "transactions", "reports"].includes(page) && (
                <button
                  className="button secondary export-button"
                  disabled={!selectedTransactions.length}
                  onClick={exportCsv}
                >
                  <Download size={16} />
                  Экспорт
                </button>
              )}
              {page !== "settings" && (
                <button
                  className="button primary"
                  disabled={!data}
                  onClick={() =>
                    page === "accounts"
                      ? setEditor({ kind: "account" })
                      : page === "categories"
                        ? setEditor({ kind: "category" })
                        : editOperation()
                  }
                >
                  <Plus size={18} />
                  {page === "accounts"
                    ? "Добавить счёт"
                    : page === "categories"
                      ? "Добавить категорию"
                      : "Добавить операцию"}
                </button>
              )}
            </div>
          </div>
          {loadError && (
            <div className="banner error" role="alert">
              <CircleAlert size={18} />
              <span>{loadError}</span>
              <button onClick={() => void refresh()}>Повторить</button>
            </div>
          )}
          {loading && !data ? (
            <div className="loading-panel">
              <Loader2 className="spin" size={28} />
              <h3>Собираем вашу финансовую картину</h3>
              <p>Загружаем счета и операции…</p>
            </div>
          ) : !data ? (
            <Empty
              icon={<CircleAlert />}
              title="Данные пока недоступны"
              text="Проверьте, что сервисы проекта запущены, и повторите загрузку."
              action={
                <button
                  className="button primary"
                  onClick={() => void refresh()}
                >
                  Повторить
                </button>
              }
            />
          ) : (
            <>
              {["overview", "transactions", "reports"].includes(page) && (
                <div className="filters-toolbar">
                  <div className="account-selector">
                    <Wallet size={17} />
                    <select
                      aria-label="Счёт для отчёта"
                      value={activeAccount?.id || ""}
                      onChange={(event) =>
                        setAccountId(Number(event.target.value))
                      }
                    >
                      {!data.accounts.length && (
                        <option value="">Нет счетов</option>
                      )}
                      {data.accounts.map((account) => (
                        <option key={account.id} value={account.id}>
                          {account.name} · {account.currency}
                        </option>
                      ))}
                    </select>
                  </div>
                  <div className="period-controls">
                    <div className="segmented" aria-label="Период отчёта">
                      {(
                        ["DAY", "WEEK", "MONTH", "YEAR", "CUSTOM"] as Period[]
                      ).map((value) => (
                        <button
                          key={value}
                          className={period === value ? "active" : ""}
                          onClick={() => setPeriod(value)}
                        >
                          {
                            {
                              DAY: "День",
                              WEEK: "Неделя",
                              MONTH: "Месяц",
                              YEAR: "Год",
                              CUSTOM: "Период",
                            }[value]
                          }
                        </button>
                      ))}
                    </div>
                    <button
                      className={`icon-button refresh-button ${loading ? "spin" : ""}`}
                      aria-label="Обновить данные"
                      disabled={loading || demo}
                      onClick={() => void refresh()}
                    >
                      <RefreshCw size={17} />
                    </button>
                  </div>
                </div>
              )}
              {["overview", "transactions", "reports"].includes(page) && (
                <div className="date-toolbar">
                  {period === "CUSTOM" ? (
                    <div className="custom-dates">
                      <label>
                        С
                        <input
                          aria-label="Начало периода"
                          type="date"
                          value={customFrom}
                          onChange={(e) => setCustomFrom(e.target.value)}
                        />
                      </label>
                      <span>—</span>
                      <label>
                        По
                        <input
                          aria-label="Конец периода"
                          type="date"
                          value={customTo}
                          onChange={(e) => setCustomTo(e.target.value)}
                        />
                      </label>
                    </div>
                  ) : (
                    <div className="date-stepper">
                      <button
                        className="icon-button"
                        aria-label="Предыдущий период"
                        onClick={() => shiftPeriod(-1)}
                      >
                        <ChevronLeft size={17} />
                      </button>
                      <label>
                        <CalendarDays size={15} />
                        <input
                          aria-label="Дата периода"
                          type="date"
                          value={anchor}
                          onChange={(e) => setAnchor(e.target.value)}
                        />
                      </label>
                      <button
                        className="icon-button"
                        aria-label="Следующий период"
                        onClick={() => shiftPeriod(1)}
                      >
                        <ChevronRight size={17} />
                      </button>
                    </div>
                  )}
                  <span className="date-caption">
                    {range
                      ? `${new Date(range.from + "T12:00:00").toLocaleDateString("ru-RU", { day: "numeric", month: "short" })} — ${new Date(range.to + "T12:00:00").toLocaleDateString("ru-RU", { day: "numeric", month: "short", year: "numeric" })}`
                      : "Проверьте даты: начало должно быть не позже конца"}
                  </span>
                  <span className="timezone-label">
                    {timezone.replaceAll("_", " ")}
                  </span>
                </div>
              )}
              {["overview", "reports"].includes(page) &&
                (activeAccount && range ? (
                  <>
                    <div className="stats-grid">
                      <Stat
                        label="Доходы за период"
                        value={formatMoney(figures.totalIncome, currency)}
                        icon={<ArrowDownLeft size={19} />}
                        tone="income"
                        detail={`${figures.incomeCount} ${plural(figures.incomeCount, "поступление", "поступления", "поступлений")}`}
                      />
                      <Stat
                        label="Расходы за период"
                        value={formatMoney(figures.totalExpense, currency)}
                        icon={<ArrowUpRight size={19} />}
                        tone="expense"
                        detail={`${figures.expenseCount} ${plural(figures.expenseCount, "операция", "операции", "операций")}`}
                      />
                      <Stat
                        label="Разница доходов и расходов"
                        value={formatMoney(figures.netAmount, currency)}
                        icon={<TrendingUp size={19} />}
                        tone="net"
                        detail="За выбранный период"
                      />
                      <Stat
                        label="Баланс счёта сейчас"
                        value={formatMoney(activeAccount.balance, currency)}
                        icon={<Wallet size={19} />}
                        tone="balance"
                        detail={activeAccount.name}
                      />
                    </div>
                    {!demo && sync !== "ready" && (
                      <div
                        className={`sync-note ${sync === "error" ? "sync-error" : ""}`}
                        role="status"
                      >
                        {sync === "error" ? (
                          <CircleAlert size={14} />
                        ) : (
                          <RefreshCw size={13} className="spin" />
                        )}
                        {sync === "error"
                          ? "Сводка временно недоступна. Ниже показан расчёт по загруженным операциям."
                          : sync === "pending"
                            ? "Сводка обновляется. Последние операции уже сохранены."
                            : "Обновляем сводку…"}
                      </div>
                    )}
                    <Suspense
                      fallback={
                        <div className="panel loading-panel">
                          <Loader2 className="spin" size={25} />
                          <p>Готовим графики…</p>
                        </div>
                      }
                    >
                      <ReportCharts
                        transactions={selectedTransactions}
                        categories={data.categories}
                        currency={currency}
                        from={range.from}
                        to={range.to}
                      />
                    </Suspense>
                    {page === "reports" ? (
                      <section className="panel category-report">
                        <div className="panel-heading">
                          <div>
                            <h2>В деталях по категориям</h2>
                            <p>Суммы и средний размер операции</p>
                          </div>
                          <span className="pill neutral">
                            {figures.transactionCount} операций
                          </span>
                        </div>
                        {!totals.categories.length ? (
                          <Empty
                            title="Пока нечего сравнивать"
                            text="Добавьте доходы или расходы за этот период."
                          />
                        ) : (
                          <div className="responsive-table">
                            <table>
                              <thead>
                                <tr>
                                  <th>Категория</th>
                                  <th>Тип</th>
                                  <th>Операций</th>
                                  <th>Средняя сумма</th>
                                  <th>Всего</th>
                                </tr>
                              </thead>
                              <tbody>
                                {totals.categories.map((row) => (
                                  <tr key={row.categoryId}>
                                    <td>
                                      <span
                                        className={`mini-dot ${row.categoryType === "INCOME" ? "green" : "purple"}`}
                                      />
                                      {data.categories.find(
                                        (c) => c.id === row.categoryId,
                                      )?.name || "Без категории"}
                                    </td>
                                    <td>
                                      <span
                                        className={`type-label ${row.categoryType === "INCOME" ? "income" : ""}`}
                                      >
                                        {row.categoryType === "INCOME"
                                          ? "Доход"
                                          : "Расход"}
                                      </span>
                                    </td>
                                    <td>{row.transactionCount}</td>
                                    <td>
                                      {formatMoney(
                                        row.totalAmount / row.transactionCount,
                                        currency,
                                      )}
                                    </td>
                                    <td>
                                      <strong>
                                        {formatMoney(row.totalAmount, currency)}
                                      </strong>
                                    </td>
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                          </div>
                        )}
                        <div className="report-footnote">
                          Данные по счёту «{activeAccount.name}», {currency}.
                          Начальный баланс не входит в доходы за период.
                        </div>
                      </section>
                    ) : (
                      <section className="panel">
                        <div className="panel-heading">
                          <div>
                            <h2>Последние операции</h2>
                            <p>Всё важное — в вашей истории</p>
                          </div>
                          <button
                            className="text-button"
                            onClick={() => setPage("transactions")}
                          >
                            Все операции
                            <ArrowRight size={15} />
                          </button>
                        </div>
                        {displayed.length ? (
                          transactionRows(displayed.slice(0, 5))
                        ) : (
                          <Empty
                            icon={<ArrowLeftRight />}
                            title="Начните свою историю"
                            text="В этом периоде пока нет операций."
                            action={
                              <button
                                className="text-button"
                                onClick={editOperation}
                              >
                                Добавить первую
                                <Plus size={15} />
                              </button>
                            }
                          />
                        )}
                      </section>
                    )}
                  </>
                ) : (
                  <Empty
                    icon={<Wallet />}
                    title={
                      range
                        ? "Ваши финансы начинаются здесь"
                        : "Уточните период"
                    }
                    text={
                      range
                        ? "Добавьте первый счёт. Затем можно записывать операции и смотреть отчёты."
                        : "Выберите корректные даты для построения отчёта."
                    }
                    action={
                      range ? (
                        <button
                          className="button primary"
                          onClick={() => setEditor({ kind: "account" })}
                        >
                          <Plus size={17} />
                          Добавить счёт
                        </button>
                      ) : undefined
                    }
                  />
                ))}
              {page === "transactions" && (
                <section className="panel">
                  <div className="transaction-toolbar">
                    <label className="search-field">
                      <Search size={17} />
                      <input
                        aria-label="Поиск операций"
                        placeholder="Найти операцию…"
                        value={query}
                        onChange={(e) => setQuery(e.target.value)}
                      />
                    </label>
                    <select
                      aria-label="Тип операции"
                      className="filter-select"
                      value={typeFilter}
                      onChange={(e) => setTypeFilter(e.target.value)}
                    >
                      <option value="ALL">Все операции</option>
                      <option value="EXPENSE">Только расходы</option>
                      <option value="INCOME">Только доходы</option>
                    </select>
                    <span className="count-label">
                      Найдено: {displayed.length}
                    </span>
                  </div>
                  {displayed.length ? (
                    transactionRows(displayed.slice(0, limit))
                  ) : (
                    <Empty
                      icon={<Search />}
                      title="Операции не найдены"
                      text="Попробуйте изменить период или условия поиска."
                    />
                  )}
                  {displayed.length > limit && (
                    <div className="load-more">
                      <button
                        className="button secondary"
                        onClick={() => setLimit(limit + 20)}
                      >
                        Показать ещё
                      </button>
                    </div>
                  )}
                </section>
              )}
              {page === "accounts" && (
                <>
                  <div className="section-hint">
                    <ShieldCheck size={17} />
                    Счета в разных валютах учитываются раздельно.
                  </div>
                  <div className="accounts-grid">
                    {data.accounts.map((account, index) => (
                      <article
                        className={`account-card account-color-${index % 3}`}
                        key={account.id}
                      >
                        <div className="account-card-top">
                          <span className="account-icon">
                            <Wallet size={22} />
                          </span>
                          <span className="currency-tag">
                            {account.currency}
                          </span>
                        </div>
                        <p>{account.name}</p>
                        <h2>
                          {formatMoney(account.balance, account.currency)}
                        </h2>
                        <div className="account-card-bottom">
                          <span>
                            {
                              data.transactions.filter(
                                (t) => t.accountId === account.id,
                              ).length
                            }{" "}
                            операций
                          </span>
                          <div>
                            <button
                              className="icon-button"
                              aria-label={`Изменить счёт ${account.name}`}
                              onClick={() =>
                                setEditor({ kind: "account", item: account })
                              }
                            >
                              <Pencil size={16} />
                            </button>
                            <button
                              className="icon-button"
                              aria-label={`Удалить счёт ${account.name}`}
                              onClick={() => removeAccount(account)}
                            >
                              <Trash2 size={16} />
                            </button>
                          </div>
                        </div>
                        <button
                          className="account-open"
                          onClick={() => {
                            setAccountId(account.id);
                            setPage("reports");
                          }}
                        >
                          Открыть отчёт
                          <ArrowUpRight size={16} />
                        </button>
                      </article>
                    ))}
                    <button
                      className="add-account-card"
                      onClick={() => setEditor({ kind: "account" })}
                    >
                      <span>
                        <Plus size={25} />
                      </span>
                      <strong>Новый счёт</strong>
                      <p>Карта, наличные или накопления</p>
                    </button>
                  </div>
                </>
              )}
              {page === "categories" && (
                <div className="category-columns">
                  {(["EXPENSE", "INCOME"] as CategoryType[]).map((type) => (
                    <section className="panel" key={type}>
                      <div className="panel-heading">
                        <div>
                          <h2>{type === "EXPENSE" ? "Расходы" : "Доходы"}</h2>
                          <p>
                            {type === "EXPENSE"
                              ? "На что вы тратите"
                              : "Откуда приходят деньги"}
                          </p>
                        </div>
                        <span
                          className={`transaction-icon ${type === "INCOME" ? "income" : "expense"}`}
                        >
                          {type === "INCOME" ? (
                            <ArrowDownLeft size={20} />
                          ) : (
                            <ArrowUpRight size={20} />
                          )}
                        </span>
                      </div>
                      {data.categories
                        .filter((c) => c.type === type)
                        .map((category, index) => (
                          <div className="category-row" key={category.id}>
                            <span
                              className={`category-mark color-${index % 5}`}
                            >
                              <Tags size={17} />
                            </span>
                            <strong>{category.name}</strong>
                            <span className="count-label">
                              {
                                data.transactions.filter(
                                  (t) => t.categoryId === category.id,
                                ).length
                              }{" "}
                              операций
                            </span>
                            <button
                              className="icon-button"
                              aria-label={`Изменить категорию ${category.name}`}
                              onClick={() =>
                                setEditor({ kind: "category", item: category })
                              }
                            >
                              <Pencil size={15} />
                            </button>
                            <button
                              className="icon-button danger-icon"
                              aria-label={`Удалить категорию ${category.name}`}
                              onClick={() => removeCategory(category)}
                            >
                              <Trash2 size={15} />
                            </button>
                          </div>
                        ))}
                      {!data.categories.some((c) => c.type === type) && (
                        <Empty
                          icon={<Tags />}
                          title="Категорий пока нет"
                          text="Добавьте удобные для вас названия."
                        />
                      )}
                    </section>
                  ))}
                </div>
              )}
              {page === "settings" && (
                <div className="settings-grid">
                  <section className="panel settings-panel">
                    <div className="panel-heading">
                      <div>
                        <h2>Ваш профиль</h2>
                        <p>Как к вам обращаться</p>
                      </div>
                      <span className="avatar large">
                        {initials(data.user.name, data.user.surname)}
                      </span>
                    </div>
                    <form
                      className="form-stack"
                      onSubmit={async (event) => {
                        event.preventDefault();
                        const form = new FormData(event.currentTarget);
                        setProfileBusy(true);
                        try {
                          const user = {
                            ...data.user,
                            name: String(form.get("name")).trim(),
                            surname: String(form.get("surname")).trim(),
                            email: String(form.get("email")).trim(),
                          };
                          if (demo) setData({ ...data, user });
                          else {
                            await api.updateUser(user);
                            await refresh();
                          }
                          notify("Профиль обновлён");
                        } catch (cause) {
                          notify(errorText(cause));
                        } finally {
                          setProfileBusy(false);
                        }
                      }}
                    >
                      <div className="form-two">
                        <label>
                          Имя
                          <input
                            name="name"
                            defaultValue={data.user.name}
                            required
                            maxLength={100}
                          />
                        </label>
                        <label>
                          Фамилия
                          <input
                            name="surname"
                            defaultValue={data.user.surname}
                            required
                            maxLength={100}
                          />
                        </label>
                      </div>
                      <label>
                        Электронная почта
                        <input
                          type="email"
                          name="email"
                          defaultValue={data.user.email}
                          required
                          maxLength={255}
                        />
                      </label>
                      <button
                        className="button primary align-start"
                        disabled={profileBusy}
                      >
                        {profileBusy ? (
                          <Loader2 className="spin" size={17} />
                        ) : (
                          <Check size={17} />
                        )}
                        Сохранить
                      </button>
                    </form>
                  </section>
                  <div className="settings-side">
                    <section className="panel settings-info">
                      <Smartphone size={25} />
                      <h2>Всегда под рукой</h2>
                      <p>
                        Откройте «Финанс» на телефоне. Установите приложение из
                        меню браузера, если эта возможность доступна.
                      </p>
                      {install && (
                        <button
                          className="button secondary"
                          onClick={async () => {
                            await install.prompt();
                            setInstall(null);
                          }}
                        >
                          Установить
                        </button>
                      )}
                    </section>
                    <section className="panel settings-info">
                      <h2>Управление аккаунтом</h2>
                      <button
                        className="button secondary full"
                        onClick={logout}
                      >
                        <LogOut size={17} />
                        Выйти из аккаунта
                      </button>
                      <button
                        className="text-button danger-text"
                        onClick={() =>
                          setConfirmation({
                            title: "Удалить аккаунт навсегда?",
                            description:
                              "Все ваши счета, категории и операции будут удалены. Восстановить их будет невозможно.",
                            phrase: "УДАЛИТЬ",
                            danger: true,
                            action: async () => {
                              if (!demo) await api.deleteUser(data.user.id);
                              logout();
                              notify("Запрос на удаление аккаунта принят");
                            },
                          })
                        }
                      >
                        Удалить аккаунт
                      </button>
                    </section>
                  </div>
                </div>
              )}
            </>
          )}
          <footer className="page-footer">
            <span>
              Финанс<span className="brand-dot">.</span>{" "}
              <span className="footer-separator">/</span> С заботой о вашем
              спокойствии
            </span>
            <span>
              <span className="live-dot" />
              {demo
                ? "Демонстрационный режим"
                : online
                  ? "Ваше финансовое пространство"
                  : "Нет подключения"}
            </span>
          </footer>
        </div>
      </main>
      <nav className="mobile-nav" aria-label="Мобильная навигация">
        {navigation.map((item) => (
          <button
            key={item.id}
            className={page === item.id ? "active" : ""}
            onClick={() => setPage(item.id)}
          >
            <item.icon size={21} />
            <span>{item.label}</span>
          </button>
        ))}
      </nav>
      {editor && data && (
        <Editor
          key={`${editor.kind}-${editor.item?.id || "new"}`}
          state={editor}
          data={data}
          accountId={activeAccount?.id || 0}
          close={() => setEditor(null)}
          save={saveEditor}
        />
      )}
      {confirmation && (
        <ConfirmDialog
          confirmation={confirmation}
          close={() => setConfirmation(null)}
        />
      )}
      {notice && (
        <div className="toast" role="status">
          <Check size={18} />
          {notice}
          <button aria-label="Скрыть уведомление" onClick={() => setNotice("")}>
            <X size={15} />
          </button>
        </div>
      )}
    </div>
  );
}

function Stat({
  label,
  value,
  icon,
  tone,
  detail,
}: {
  label: string;
  value: string;
  icon: ReactNode;
  tone: string;
  detail: string;
}) {
  return (
    <article className={`stat-card stat-${tone}`}>
      <div className="stat-top">
        <span>{label}</span>
        <span className="stat-icon">{icon}</span>
      </div>
      <strong>{value}</strong>
      <small>{detail}</small>
    </article>
  );
}
function Empty({
  icon,
  title,
  text,
  action,
}: {
  icon?: ReactNode;
  title: string;
  text: string;
  action?: ReactNode;
}) {
  return (
    <div className="empty-state">
      {icon && <span className="empty-icon">{icon}</span>}
      <h3>{title}</h3>
      <p>{text}</p>
      {action}
    </div>
  );
}
function plural(count: number, one: string, few: string, many: string) {
  const n = count % 100;
  return n >= 11 && n <= 14
    ? many
    : count % 10 === 1
      ? one
      : count % 10 >= 2 && count % 10 <= 4
        ? few
        : many;
}
