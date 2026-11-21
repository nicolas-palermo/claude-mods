import { getSettings } from "@/lib/db";
import { isQuiet } from "@/lib/quiet";
import { saveSettings } from "../../actions";

export default async function SettingsPage() {
  const s = await getSettings();
  const quietNow = s.quiet_enabled && isQuiet(new Date(), s.quiet_start, s.quiet_end, s.timezone);
  return (
    <>
      <h1>Ajustes</h1>
      <form action={saveSettings}>
        <div className="card">
          <div className="row">
            <strong className="grow">Horario de silencio</strong>
            {quietNow && <span className="pill warn">ahora en silencio</span>}
          </div>
          <p className="muted small">Durante la franja no se mandan luces ni sonido. Los eventos igual quedan en el historial. El botón Probar lo ignora.</p>
          <div className="row">
            <input type="checkbox" name="quiet_enabled" id="quiet_enabled" defaultChecked={s.quiet_enabled} />
            <label htmlFor="quiet_enabled" style={{ margin: 0 }}>Activado</label>
          </div>
          <div className="row">
            <div>
              <label>Desde</label>
              <input type="time" name="quiet_start" defaultValue={s.quiet_start.slice(0, 5)} />
            </div>
            <div>
              <label>Hasta</label>
              <input type="time" name="quiet_end" defaultValue={s.quiet_end.slice(0, 5)} />
            </div>
          </div>
          <label>Zona horaria</label>
          <input name="timezone" defaultValue={s.timezone} style={{ width: "100%" }} />
        </div>
        <div className="card">
          <strong>Agrupamiento</strong>
          <p className="muted small">
            Los eventos que llegan dentro de esta ventana (contada desde el primero) se juntan en un lote y suena una sola
            animación: la de la regla con mayor prioridad. 0 = sin agrupar.
          </p>
          <label>Ventana en segundos (0 a 50)</label>
          <input type="number" name="batch_window_seconds" min={0} max={50} defaultValue={s.batch_window_seconds} style={{ width: 100 }} />
        </div>
        <button className="primary">Guardar</button>
      </form>
    </>
  );
}
