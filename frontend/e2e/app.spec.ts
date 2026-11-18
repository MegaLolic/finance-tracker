import { test, expect, type Page } from "@playwright/test";

async function navigate(page: Page, name: string) {
  const mobile = (page.viewportSize()?.width || 1440) <= 760;
  await page
    .getByRole("navigation", {
      name: mobile ? "Мобильная навигация" : "Основная навигация",
    })
    .getByRole("button", { name, exact: true })
    .click();
}
async function noOverflow(page: Page) {
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth + 1,
    ),
  ).toBe(true);
}

test("demo reports, navigation, real interactive charts and local CRUD", async ({
  page,
}, info) => {
  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: "С возвращением" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Посмотреть демо" }).click();
  await expect(
    page.getByRole("heading", { name: "Привет, Александр!" }),
  ).toBeVisible();
  await expect(page.locator(".recharts-surface").first()).toBeVisible();
  await noOverflow(page);
  await page.screenshot({
    path: `../target/frontend-${info.project.name}-overview.png`,
    fullPage: true,
  });
  await navigate(page, "Отчёты");
  await expect(
    page.getByRole("heading", { name: "В деталях по категориям" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Год", exact: true }).click();
  await expect(page.locator(".chart-trend")).toBeVisible();
  await page.getByRole("button", { name: "Период", exact: true }).click();
  await page.getByLabel("Начало периода").fill("2026-11-20");
  await page.getByLabel("Конец периода").fill("2026-11-01");
  await expect(
    page.getByRole("heading", { name: "Уточните период" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Месяц", exact: true }).click();
  await navigate(page, "Счета");
  await page
    .getByRole("button", { name: "Добавить счёт", exact: true })
    .click();
  await page.getByLabel("Название", { exact: true }).fill("Тестовый кошелёк");
  await page.getByLabel("Начальный баланс").fill("100");
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Добавить", exact: true })
    .click();
  await expect(
    page.getByText("Тестовый кошелёк", { exact: true }),
  ).toBeVisible();
  await navigate(page, "Операции");
  await page.getByRole("button", { name: "Добавить операцию" }).click();
  await page.getByLabel("Сумма", { exact: true }).fill("12,34");
  await page
    .getByRole("combobox", { name: "Категория", exact: true })
    .selectOption({ label: "Продукты" });
  await page
    .getByRole("dialog")
    .getByLabel("Комментарий", { exact: false })
    .fill("Проверка интерфейса");
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Добавить", exact: true })
    .click();
  await expect(
    page.getByText("Проверка интерфейса", { exact: true }),
  ).toBeVisible();
  await page.getByLabel("Поиск операций").fill("ничего-не-найти");
  await expect(
    page.getByRole("heading", { name: "Операции не найдены" }),
  ).toBeVisible();
  await noOverflow(page);
  expect(errors).toEqual([]);
});

test("real registration, account, category, operation, report, update and deletion", async ({
  page,
  request,
}) => {
  const email = `frontend-${Date.now()}-${Math.random().toString(16).slice(2)}@example.com`;
  const password = "Frontend-test-Aa123!";
  let token = "";
  let id = 0;
  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  try {
    await page.goto("/");
    await page
      .getByRole("button", { name: "Зарегистрироваться", exact: true })
      .click();
    await page.getByLabel("Имя", { exact: true }).fill("Тест");
    await page.getByLabel("Фамилия", { exact: true }).fill("Интерфейса");
    await page.getByLabel("Электронная почта", { exact: true }).fill(email);
    await page.getByLabel("Пароль", { exact: true }).fill(password);
    await page
      .getByRole("button", { name: "Создать аккаунт", exact: true })
      .click();
    await expect(
      page.getByRole("heading", { name: "Привет, Тест!" }),
    ).toBeVisible();
    token = await page.evaluate(
      () =>
        JSON.parse(sessionStorage.getItem("finance-assistance.session") || "{}")
          .token || "",
    );
    const user = await request.get("/api/users/me", {
      headers: { Authorization: `Bearer ${token}` },
    });
    id = (await user.json()).id;
    await navigate(page, "Счета");
    await page
      .getByRole("button", { name: "Добавить счёт", exact: true })
      .click();
    await page.getByLabel("Название", { exact: true }).fill("Основной");
    await page.getByLabel("Начальный баланс").fill("100");
    await page
      .getByRole("dialog")
      .getByRole("button", { name: "Добавить", exact: true })
      .click();
    await expect(page.getByText("Основной", { exact: true })).toBeVisible();
    await navigate(page, "Категории");
    await page
      .getByRole("button", { name: "Добавить категорию", exact: true })
      .click();
    await page.getByLabel("Название", { exact: true }).fill("Еда");
    await page
      .getByRole("dialog")
      .getByRole("button", { name: "Добавить", exact: true })
      .click();
    await expect(page.getByText("Еда", { exact: true })).toBeVisible();
    await navigate(page, "Операции");
    await page
      .getByRole("button", { name: "Добавить операцию", exact: true })
      .click();
    await page.getByLabel("Сумма", { exact: true }).fill("42,35");
    await page
      .getByRole("combobox", { name: "Категория", exact: true })
      .selectOption({ label: "Еда" });
    await page
      .getByLabel("Комментарий", { exact: false })
      .fill("Тестовая покупка");
    await page
      .getByRole("dialog")
      .getByRole("button", { name: "Добавить", exact: true })
      .click();
    await expect(
      page.getByText("Тестовая покупка", { exact: true }),
    ).toBeVisible();
    await navigate(page, "Отчёты");
    await expect(page.locator(".stat-expense > strong")).toContainText("42,35");
    await expect(page.locator(".stat-balance > strong")).toContainText("57,65");
    await expect
      .poll(async () => page.locator(".sync-note").count(), { timeout: 35_000 })
      .toBe(0);
    await expect(page.locator(".recharts-surface").first()).toBeVisible();
    await noOverflow(page);
    await navigate(page, "Операции");
    await page
      .getByRole("button", { name: "Изменить операцию", exact: true })
      .click();
    await page.getByLabel("Сумма", { exact: true }).fill("12,34");
    await page
      .getByRole("dialog")
      .getByRole("button", { name: "Сохранить", exact: true })
      .click();
    await expect(page.getByRole("dialog")).toHaveCount(0);
    await page
      .getByRole("button", { name: "Удалить операцию", exact: true })
      .click();
    await page
      .getByRole("dialog")
      .getByRole("button", { name: "Удалить", exact: true })
      .click();
    await expect(
      page.getByRole("heading", { name: "Операции не найдены" }),
    ).toBeVisible();
    await navigate(page, "Обзор");
    await expect(page.locator(".stat-balance > strong")).toContainText(
      "100,00",
    );
    expect(errors).toEqual([]);
  } finally {
    if (!token) {
      const auth = await request.post("/api/auth/sign-in", {
        data: { email, password },
      });
      if (auth.ok()) token = (await auth.json()).token;
    }
    if (token) {
      if (!id) {
        const user = await request.get("/api/users/me", {
          headers: { Authorization: `Bearer ${token}` },
        });
        if (user.ok()) id = (await user.json()).id;
      }
      if (id) {
        const deleted = await request.delete(`/api/users/${id}`, {
          headers: { Authorization: `Bearer ${token}` },
        });
        expect([202, 401]).toContain(deleted.status());
      }
    }
  }
});

test("installable manifest and offline shell never cache personal API", async ({
  page,
  context,
  request,
}) => {
  const manifestResponse = await request.get("/manifest.webmanifest");
  expect(manifestResponse.ok()).toBe(true);
  const manifest = await manifestResponse.json();
  expect(manifest.display).toBe("standalone");
  expect(manifest.icons.map((icon: { sizes: string }) => icon.sizes)).toEqual([
    "192x192",
    "512x512",
  ]);
  for (const icon of manifest.icons)
    expect((await request.get(icon.src)).headers()["content-type"]).toBe(
      "image/png",
    );
  await page.goto("/");
  await page.evaluate(async () => {
    await navigator.serviceWorker.ready;
  });
  await page.reload();
  await page.getByRole("button", { name: "Посмотреть демо" }).click();
  await expect(page.locator(".recharts-surface").first()).toBeVisible();
  const cachedPaths = await page.evaluate(async () => {
    const cacheNames = await caches.keys();
    const requests = await Promise.all(
      cacheNames.map(async (name) => (await caches.open(name)).keys()),
    );
    return requests.flat().map((item) => new URL(item.url).pathname);
  });
  expect(cachedPaths.some((path) => path.startsWith("/api"))).toBe(false);
  await context.setOffline(true);
  await page.reload();
  await expect(
    page.getByRole("heading", { name: "С возвращением" }),
  ).toBeVisible();
  await context.setOffline(false);
});
