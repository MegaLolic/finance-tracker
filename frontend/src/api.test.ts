import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const initialPair = {
  token: "initial-access",
  refreshToken: "initial-refresh",
};
const renewedPair = {
  token: "renewed-access",
  refreshToken: "renewed-refresh",
};
const credentials = {
  email: "test@example.com",
  password: "private-password-123",
};
const accountInput = {
  name: "Основной",
  balance: 100,
  currency: "BYN" as const,
};

let stored: Map<string, string>;
let browser: EventTarget;
let fetchMock: ReturnType<typeof vi.fn<typeof fetch>>;

function json(status: number, value: unknown): Response {
  return new Response(JSON.stringify(value), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function deferred(): { promise: Promise<void>; release: () => void } {
  let release: () => void = () => undefined;
  const promise = new Promise<void>((resolve) => {
    release = resolve;
  });
  return { promise, release };
}

async function signedInApi() {
  const module = await import("./api");
  fetchMock.mockResolvedValueOnce(json(200, initialPair));
  await module.api.signIn(credentials);
  fetchMock.mockClear();
  return module;
}

beforeEach(() => {
  vi.resetModules();
  stored = new Map<string, string>();
  browser = new EventTarget();
  fetchMock = vi.fn<typeof fetch>();
  vi.stubGlobal("sessionStorage", {
    getItem: (key: string) => stored.get(key) ?? null,
    setItem: (key: string, value: string) => stored.set(key, value),
    removeItem: (key: string) => stored.delete(key),
  });
  vi.stubGlobal("window", browser);
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("API authorization and safe retries", () => {
  it("refreshes once when all parallel load requests receive 401 and retries them with the renewed token", async () => {
    const { api } = await signedInApi();
    const gate = deferred();
    let refreshCount = 0;
    let renewedRequests = 0;
    fetchMock.mockImplementation(async (input, options) => {
      const path = String(input);
      if (path === "/api/auth/refresh") {
        refreshCount++;
        expect(JSON.parse(String(options?.body))).toEqual({
          refreshToken: initialPair.refreshToken,
        });
        expect(new Headers(options?.headers).has("Authorization")).toBe(false);
        await gate.promise;
        return json(200, renewedPair);
      }
      if (
        new Headers(options?.headers).get("Authorization") ===
        `Bearer ${initialPair.token}`
      ) {
        return json(401, {});
      }
      expect(new Headers(options?.headers).get("Authorization")).toBe(
        `Bearer ${renewedPair.token}`,
      );
      renewedRequests++;
      return json(
        200,
        path === "/api/users/me"
          ? { id: 7, name: "Test", surname: "User", email: credentials.email }
          : [],
      );
    });

    const pending = api.load();
    await vi.waitFor(() => expect(refreshCount).toBe(1));
    gate.release();
    const data = await pending;

    expect(data).toEqual({
      user: { id: 7, name: "Test", surname: "User", email: credentials.email },
      accounts: [],
      categories: [],
      transactions: [],
    });
    expect(refreshCount).toBe(1);
    expect(renewedRequests).toBe(4);
    expect(fetchMock).toHaveBeenCalledTimes(9);
    expect([...stored.values()]).toEqual([JSON.stringify(renewedPair)]);
  });

  it("does not replay a create request after an ambiguous network failure", async () => {
    const { api, ApiError } = await signedInApi();
    fetchMock.mockRejectedValue(
      new TypeError("Connection lost after request transmission"),
    );

    const failure = await api
      .createAccount(accountInput)
      .catch((error) => error);

    expect(failure).toBeInstanceOf(ApiError);
    expect(failure.status).toBe(0);
    expect(failure.message).toContain("проверьте список перед повтором");
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0][0]).toBe("/api/finance/accounts");
    expect(fetchMock.mock.calls[0][1]?.method).toBe("POST");
    expect(api.hasSession()).toBe(true);
  });

  it("clears the session and emits one expiration event if a shared refresh is rejected", async () => {
    const { api } = await signedInApi();
    const expired = vi.fn();
    browser.addEventListener("session-expired", expired);
    fetchMock.mockImplementation(async () =>
      json(401, { message: "Invalid refresh token" }),
    );

    await expect(api.load()).rejects.toMatchObject({ status: 401 });

    expect(
      fetchMock.mock.calls.filter(([path]) => path === "/api/auth/refresh"),
    ).toHaveLength(1);
    expect(api.hasSession()).toBe(false);
    expect(stored.size).toBe(0);
    expect(expired).toHaveBeenCalledTimes(1);
  });

  it("keeps the session on a transient refresh network failure so the user can retry later", async () => {
    const { api } = await signedInApi();
    const expired = vi.fn();
    browser.addEventListener("session-expired", expired);
    fetchMock.mockImplementation(async (input) => {
      if (String(input) === "/api/auth/refresh")
        throw new TypeError("Network offline");
      return json(401, {});
    });

    await expect(api.load()).rejects.toMatchObject({ status: 0 });

    expect(api.hasSession()).toBe(true);
    expect(expired).not.toHaveBeenCalled();
    expect([...stored.values()]).toEqual([JSON.stringify(initialPair)]);
  });

  it("prevents an in-flight refresh from restoring a logged-out session", async () => {
    const { api } = await signedInApi();
    const gate = deferred();
    let refreshStarted = false;
    fetchMock.mockImplementation(async (input) => {
      if (String(input) === "/api/auth/refresh") {
        refreshStarted = true;
        await gate.promise;
        return json(200, renewedPair);
      }
      return json(401, {});
    });
    const pending = api.load();
    const rejected = expect(pending).rejects.toMatchObject({ status: 401 });
    await vi.waitFor(() => expect(refreshStarted).toBe(true));

    api.logout();
    gate.release();
    await rejected;

    expect(api.hasSession()).toBe(false);
    expect(stored.size).toBe(0);
  });

  it("does not discard an existing session when a separate sign-in attempt fails", async () => {
    const { api } = await signedInApi();
    const expired = vi.fn();
    browser.addEventListener("session-expired", expired);
    fetchMock.mockResolvedValue(
      json(401, { message: "Email or password is not correct" }),
    );

    await expect(
      api.signIn({ ...credentials, password: "wrong-password" }),
    ).rejects.toMatchObject({
      status: 401,
      message: "Неверный email или пароль.",
    });

    expect(api.hasSession()).toBe(true);
    expect(expired).not.toHaveBeenCalled();
    expect([...stored.values()]).toEqual([JSON.stringify(initialPair)]);
  });

  it("persists only the token pair and removes it on logout", async () => {
    const { api } = await signedInApi();

    expect([...stored.values()]).toEqual([JSON.stringify(initialPair)]);
    expect([...stored.values()].join("")).not.toContain(credentials.password);
    expect([...stored.values()].join("")).not.toContain(credentials.email);
    api.logout();

    expect(stored.size).toBe(0);
    expect(api.hasSession()).toBe(false);
    await expect(api.load()).rejects.toMatchObject({ status: 401 });
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe("API errors", () => {
  it("maps validation field errors to useful Russian messages without exposing backend text", async () => {
    const { api, ApiError } = await signedInApi();
    fetchMock.mockResolvedValue(
      json(400, {
        message: "Validation failed",
        errors: {
          amount: "must be greater than 0",
          accountId: "must not be null",
        },
      }),
    );

    const failure = await api
      .createTransaction({
        accountId: 0,
        categoryId: 2,
        amount: 0,
        description: null,
      })
      .catch((error) => error);

    expect(failure).toBeInstanceOf(ApiError);
    expect(failure.status).toBe(400);
    expect(failure.message).toBe("Проверьте заполненные поля.");
    expect(failure.errors).toEqual({
      amount: "Укажите положительную сумму с двумя знаками после запятой.",
      accountId: "Выберите счёт.",
    });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("explains stale version conflicts and does not retry the mutation", async () => {
    const { api } = await signedInApi();
    fetchMock.mockResolvedValue(
      json(409, {
        message: "Data has changed. Reload it and retry the operation.",
      }),
    );

    await expect(
      api.updateAccount({ ...accountInput, id: 3, version: 0 }),
    ).rejects.toMatchObject({
      status: 409,
      message: "Данные изменились. Обновите страницу и повторите действие.",
    });

    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("hides unknown internal failure details from the UI", async () => {
    const { api } = await signedInApi();
    fetchMock.mockResolvedValue(
      json(500, { message: "jdbc:postgresql://internal-host secret-value" }),
    );

    const failure = await api
      .createAccount(accountInput)
      .catch((error) => error);

    expect(failure.status).toBe(500);
    expect(failure.message).toBe(
      "Не удалось выполнить действие. Попробуйте позже.",
    );
    expect(failure.message).not.toContain("internal-host");
    expect(failure.message).not.toContain("secret-value");
  });

  it("does not replay a mutation when the response body cannot be read", async () => {
    const { api } = await signedInApi();
    const response = json(201, {});
    vi.spyOn(response, "text").mockRejectedValue(
      new TypeError("stream interrupted"),
    );
    fetchMock.mockResolvedValue(response);

    await expect(api.createAccount(accountInput)).rejects.toMatchObject({
      status: 0,
    });

    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
