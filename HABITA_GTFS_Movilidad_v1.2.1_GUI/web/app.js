'use strict';
const $ = id => document.getElementById(id);
const esc = value => String(value ?? '—').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const fmt = (value, digits=0) => value == null ? '—' : Number(value).toLocaleString('es-CL', {maximumFractionDigits:digits});
const isoLabel = value => { if(!value) return '—'; const parts = value.split('-'); return parts.length===3 ? parts.reverse().join('/') : value; };
let config, report, currentJob, busy=false;
let reportStale=false;
const controls = ['preset','name','lat','lon','date','analyze','feed-file','feed-path','load-path'];

async function api(path, body, extras={}) {
  const response = await fetch(path, {method: body===undefined ? 'GET':'POST',
    headers: body===undefined ? {} : {'Content-Type':'application/json','X-Habita-Token':config.token,...extras},
    body: body===undefined ? undefined : JSON.stringify(body)});
  const data = await response.json();
  if (!response.ok) throw new Error(data.error || 'No se pudo completar la solicitud.');
  return data;
}
function setBusy(value,label='Procesando…') {
  busy=value;
  controls.forEach(id => $(id).disabled=value);
  $('busy').classList.toggle('hidden',!value);
  $('busy-label').textContent=label;
  $('analyze').querySelector('span').textContent=value ? 'Analizando…' : 'Analizar ubicación';
}
function message(text,success=false) {
  $('message').textContent=text;
  $('message').className='message'+(success?' success':'');
}
function clearMessage(){ $('message').classList.add('hidden'); }
function setFeed(feed) {
  config.feed=feed;
  $('feed-version').textContent=feed.version;
  $('feed-validity').textContent=isoLabel(feed.start)+' — '+isoLabel(feed.end);
  $('feed-label').textContent=feed.label;
  $('date').min=feed.start; $('date').max=feed.end;
  const selected=$('date').value;
  if(!selected || selected<feed.start || selected>feed.end) $('date').value=feed.suggested_date;
}
function stale() {
  if(!report) return;
  reportStale=true;
  $('report-context').innerHTML='<div class="stale-banner">Cambiaste la consulta o la fuente. Los resultados de abajo siguen correspondiendo al análisis anterior; pulsa «Analizar ubicación» para actualizarlos.</div>'+context(report);
}
function context(r) {
  return 'Consulta del <strong>'+esc(isoLabel(r.metadata.analysis_date))+'</strong> · '+esc(r.location.lat)+', '+esc(r.location.lon)+' · Feed '+esc(r.metadata.feed_version);
}
function metric(label,value,unit,detail) {
  return '<article class="metric"><span class="label">'+esc(label)+'</span><span class="value">'+esc(value)+' <span class="unit">'+esc(unit)+'</span></span><small>'+esc(detail)+'</small></article>';
}
function card(title,sub,content) {
  return '<article class="content-card"><h3>'+esc(title)+'</h3>'+(sub?'<p class="sub">'+esc(sub)+'</p>':'')+content+'</article>';
}
function table(headers,rows) {
  if(!rows.length) return '<p class="note">No hay registros para esta fecha y ubicación.</p>';
  return '<div class="table-wrap"><table><thead><tr>'+headers.map(x=>'<th scope="col">'+esc(x)+'</th>').join('')+'</tr></thead><tbody>'+rows.map(r=>'<tr>'+r.map(x=>'<td>'+esc(x)+'</td>').join('')+'</tr>').join('')+'</tbody></table></div>';
}
function routeName(row){return row.route_short_name || row.route_id;}
function stat(label,value){return '<div class="coverage-stats"><span>'+esc(label)+'</span><strong>'+esc(value)+'</strong></div>';}
function stopRow(label,stop) {
  if(!stop.stop_id) return '<div class="stop-row"><div><small>'+esc(label)+'</small><strong>Sin paradero activo en el radio de búsqueda</strong><p class="note">'+esc(stop.note)+'</p></div></div>';
  return '<div class="stop-row"><div><small>'+esc(label)+'</small><strong>'+esc(stop.stop_name)+'</strong></div><div class="stop-distance">'+fmt(stop.geodesic_distance_m,1)+' <span class="unit">m</span></div></div>';
}
function renderSummary(r) {
  const active=r.nearest_active_stop, physical=r.nearest_physical_stop;
  const service=active.service_window, peaks=active.peak_departures;
  let nearest=stopRow('PARADERO FÍSICO MÁS CERCANO',physical)+stopRow('PARADERO ACTIVO MÁS CERCANO · FECHA CONSULTADA',active);
  nearest+='<p class="note">Búsqueda hasta 1.000 m. Las distancias son geodésicas, en línea recta.</p>';
  if(active.same_as_nearest_physical_stop) nearest+='<span class="tag">El paradero más cercano tiene servicio activo</span>';
  let schedule=service?'<div class="service-line"><div><small>PRIMERA SALIDA</small><strong>'+esc(service.first_departure)+'</strong></div><div><small>ÚLTIMA SALIDA</small><strong>'+esc(service.last_departure)+'</strong></div></div>':'<p class="note">Sin ventana de servicio disponible.</p>';
  if(service?.note) schedule+='<p class="note">'+esc(service.note)+'</p>';
  schedule+=stat('Salidas programadas · 07:00–09:00',fmt(peaks?.morning_peak?.total_departures));
  schedule+=stat('Salidas programadas · 17:00–19:00',fmt(peaks?.afternoon_peak?.total_departures));
  schedule+='<p class="note">Conteos en el paradero activo más cercano. Incluyen estimaciones cuando el feed usa frecuencias.</p>';
  const chips=(active.headway_by_route_direction||[]).map(x=>'<span class="route-chip">'+esc(routeName(x))+' · '+esc(x.direction_id)+'</span>').join('');
  $('summary').innerHTML=card('Tus conexiones más cercanas','Proximidad física y disponibilidad de servicio son cosas distintas.',nearest)+
    card('Ventana de servicio','Horarios del paradero activo más cercano.',schedule)+
    card('Recorridos en ese paradero',fmt(active.route_count)+' recorridos · identificador de sentido según GTFS.', '<div class="route-chips">'+(chips||'<span class="note">Sin recorridos activos</span>')+'</div>');
}
function renderRoutes(r) {
  $('routes').innerHTML=card('Recorridos y frecuencias','Información del paradero activo más cercano, para la fecha consultada.',
    '<div class="filter"><label for="route-filter">Buscar recorrido o destino</label><input id="route-filter" type="search" placeholder="Ej.: 201, La Cisterna…"></div><div id="routes-table"></div><p class="legend">Cada fila corresponde a un recorrido y un sentido (0 / 1 según el feed). Los intervalos se expresan en minutos.</p>')+
    card('Destinos y servicios','Relación entre recorridos, sentidos y destinos declarados.', '<div id="destinations-table"></div>')+
    card('Cómo leer las frecuencias','', '<div class="detail-note"><strong>Intervalo ponderado:</strong> promedio de los intervalos declarados en bloques de frecuencia, ponderados por su solapamiento con la ventana punta y desplazados hasta el paradero.<br><strong>Intervalo entre salidas:</strong> (última salida − primera salida) / (cantidad − 1). No representa una espera garantizada.<br><strong>Patrones simultáneos:</strong> si aparecen, el promedio ponderado no equivale a la frecuencia combinada de todos los servicios.<br>«—» significa que el motor no dispone de un valor para esa ventana.</div>');
  const draw=()=>{
    const term=$('route-filter').value.toLocaleLowerCase('es');
    const active=r.nearest_active_stop;
    const rows=(active.headway_by_route_direction||[]).filter(x=>(routeName(x)+' '+x.route_long_name).toLocaleLowerCase('es').includes(term)).map(x=>{
      const m=x.headway.morning_peak, a=x.headway.afternoon_peak;
      return [routeName(x),x.direction_id,x.route_long_name,
        fmt(m?.weighted_block_headway_min,2),fmt(m?.effective_interval_from_generated_departures_min,2),fmt(m?.scheduled_departures),
        fmt(a?.weighted_block_headway_min,2),fmt(a?.effective_interval_from_generated_departures_min,2),fmt(a?.scheduled_departures),
        m?.concurrent_patterns_detected||a?.concurrent_patterns_detected?'Sí':'No'];
    });
    $('routes-table').innerHTML=table(['Recorrido','Sentido','Nombre','Pond. AM (min)','Entre salidas AM (min)','Salidas AM','Pond. PM (min)','Entre salidas PM (min)','Salidas PM','Patrones simultáneos'],rows);
    $('destinations-table').innerHTML=table(['Recorrido','Sentido','Destino','Servicio'],
      (active.destinations||[]).filter(x=>(routeName(x)+' '+x.trip_headsign+' '+x.route_long_name).toLocaleLowerCase('es').includes(term)).map(x=>[routeName(x),x.direction_id,x.trip_headsign||'No declarado',x.service_id]));
  };
  $('route-filter').addEventListener('input',draw);draw();
}
function renderCoverage(r) {
  const keys=['radius_500m','radius_800m'];
  let totals='<div class="two-cols">';
  for(const key of keys){
    const area=r.area_analysis[key];
    let body='<span class="coverage-total">'+fmt(area.stop_points)+'</span><span class="cover-label">Puntos de parada en el radio</span>'+
      stat('Recorridos con servicio activo',fmt(area.active_routes_count));
    for(const [peak,label] of [['morning_peak','Punta mañana · 07:00–09:00'],['afternoon_peak','Punta tarde · 17:00–19:00']]){
      body+='<div class="peak-card"><h4>'+label+'</h4>'+stat('Eventos en paraderos',fmt(area[peak].stop_departure_events))+stat('Instancias únicas de viaje',fmt(area[peak].unique_trip_instances))+'</div>';
    }
    body+='<div class="route-chips">'+area.active_routes.map(x=>'<span class="route-chip">'+esc(routeName(x))+'</span>').join('')+'</div>';
    totals+=card('Radio de '+fmt(area.radius_m)+' m','Área alrededor de la ubicación consultada.',body);
  }
  totals+='</div>';
  totals+=card('Dos conteos, dos lecturas','', '<div class="detail-note"><strong>Eventos en paraderos:</strong> un mismo viaje puede contarse varias veces si pasa por varios paraderos del radio.<br><strong>Instancias únicas de viaje:</strong> cada viaje generado se cuenta una sola vez en la ventana. No equivale a vehículos físicos distintos.<br><strong>Puntos de parada:</strong> puntos del feed dentro del radio; no todos tienen necesariamente servicio activo en la fecha consultada.</div>');
  for(const key of keys){
    const a=r.area_analysis[key], names=Object.fromEntries(a.active_routes.map(x=>[x.route_id,routeName(x)]));
    const ids=[...new Set([...Object.keys(a.morning_peak.by_route),...Object.keys(a.afternoon_peak.by_route)])].sort((x,y)=>x.localeCompare(y,'es',{numeric:true}));
    totals+=card('Desglose por recorrido · '+fmt(a.radius_m)+' m','Eventos e instancias de viaje durante cada ventana punta.',
      table(['Recorrido','Eventos AM','Viajes únicos AM','Eventos PM','Viajes únicos PM'],ids.map(id=>[names[id]||id,fmt(a.morning_peak.by_route[id]?.stop_departure_events??0),fmt(a.morning_peak.by_route[id]?.unique_trip_instances??0),fmt(a.afternoon_peak.by_route[id]?.stop_departure_events??0),fmt(a.afternoon_peak.by_route[id]?.unique_trip_instances??0)])));
  }
  $('coverage').innerHTML=totals;
}
function renderTechnical(r) {
  const m=r.metadata;
  const meta=[['Versión de feed',m.feed_version],['Fecha consultada',isoLabel(m.analysis_date)],['Vigencia',isoLabel(m.feed_start_date.replace(/^(\d{4})(\d{2})(\d{2})$/,'$1-$2-$3'))+' — '+isoLabel(m.feed_end_date.replace(/^(\d{4})(\d{2})(\d{2})$/,'$1-$2-$3'))],['Zona horaria',m.timezone||'UTC'],['Generado',m.generated_at||m.generated_at_utc],['Servicios activos',m.active_service_ids.join(', ')||'Ninguno'],['Motor', 'GTFS Analyzer 1.2.1'],['Puntaje HABITA','Este módulo no calcula un índice compuesto.']];
  $('technical').innerHTML=card('Trazabilidad del análisis','Datos de origen y configuración del resultado.', '<dl class="technical-list">'+meta.map(([a,b])=>'<dt>'+esc(a)+'</dt><dd>'+esc(b)+'</dd>').join('')+'</dl>')+
    card('Alcance y limitaciones','Aclaraciones entregadas por el motor GTFS.', '<ul class="limitations">'+r.limitations.map(x=>'<li>'+esc(x)+'</li>').join('')+'</ul>')+
    card('Informe completo','Misma estructura JSON del analizador original.', '<details><summary>Mostrar todos los datos <span>+</span></summary><pre id="raw-json"></pre></details>');
  $('raw-json').textContent=JSON.stringify(r,null,2);
}
function showTab(id,focus=false) {
  document.querySelectorAll('[role="tab"]').forEach(b=>{
    const selected=b.dataset.tab===id;b.setAttribute('aria-selected',String(selected));b.tabIndex=selected?0:-1;
    if(selected&&focus)b.focus();
  });
  document.querySelectorAll('.tab-panel').forEach(p=>p.classList.toggle('hidden',p.id!==id));
}
function render(r) {
  report=r;reportStale=false;
  $('empty-state').classList.add('hidden');$('report').classList.remove('hidden');$('export').classList.remove('hidden');
  $('result-title').textContent=r.location.name;
  $('report-context').innerHTML=context(r);
  const p=r.nearest_physical_stop,a=r.nearest_active_stop,area=r.area_analysis.radius_800m;
  $('metrics').innerHTML=metric('Paradero físico más cercano',fmt(p.geodesic_distance_m,1),'m','Distancia en línea recta')+
    metric('Caminata teórica',fmt(p.theoretical_walk_time_min,1),'min','Hacia ese paradero · 1,4 m/s')+
    metric('Recorridos activos',fmt(area.active_routes_count),'','Dentro de 800 m')+
    metric('Puntos de parada',fmt(area.stop_points),'','Dentro de 800 m');
  renderSummary(r);renderRoutes(r);renderCoverage(r);renderTechnical(r);showTab('summary');
}
async function poll(jobId) {
  for(;;) {
    const job=await api('/api/jobs/'+encodeURIComponent(jobId));
    if(job.status==='done'){render(job.result);return;}
    if(job.status==='error')throw new Error(job.error);
    $('busy-label').textContent=job.phase;
    await new Promise(resolve=>setTimeout(resolve,650));
  }
}
$('query-form').addEventListener('submit',async event=>{
  event.preventDefault();if(busy)return;clearMessage();setBusy(true,'La primera carga puede tardar unos segundos…');
  try{
    const input={name:$('name').value,lat:$('lat').value,lon:$('lon').value,date:$('date').value};
    const task=await api('/api/analyze',input);currentJob=task.job_id;await poll(currentJob);
  }catch(error){message(error.message);}finally{setBusy(false);}
});
$('preset').addEventListener('change',()=>{
  const example=config.examples[$('preset').value];
  if(example){$('name').value=example.name;$('lat').value=example.lat;$('lon').value=example.lon;}
  else {$('name').value='';$('name').focus();}
  stale();
});
['name','lat','lon','date'].forEach(id=>$(id).addEventListener('input',()=>{
  if(id!=='date')$('preset').value='custom';
  stale();
}));
document.querySelectorAll('[role="tab"]').forEach((b,i,all)=>{
  b.addEventListener('click',()=>showTab(b.dataset.tab));
  b.addEventListener('keydown',e=>{
    let next;
    if(e.key==='ArrowRight')next=(i+1)%all.length;
    if(e.key==='ArrowLeft')next=(i+all.length-1)%all.length;
    if(e.key==='Home')next=0;
    if(e.key==='End')next=all.length-1;
    if(next!==undefined){e.preventDefault();showTab(all[next].dataset.tab,true);}
  });
});
$('export').addEventListener('click',async()=>{
  if(!report)return;
  // Exportar el informe visible, incluso si se modificó luego el formulario.
  const blob=new Blob([JSON.stringify(report,null,2)],{type:'application/json;charset=utf-8'});
  const url=URL.createObjectURL(blob),a=document.createElement('a');
  a.href=url;a.download='HABITA_movilidad_'+report.metadata.analysis_date+'.json';a.click();
  setTimeout(()=>URL.revokeObjectURL(url),1000);
});
$('feed-file').addEventListener('change',async()=>{
  const file=$('feed-file').files[0];if(!file)return;
  if(file.size>300*1024*1024){message('El ZIP debe pesar como máximo 300 MB.');$('feed-file').value='';return;}
  clearMessage();setBusy(true,'Cargando y validando el nuevo feed…');
  try{
    const response=await fetch('/api/feed/upload',{method:'POST',headers:{'Content-Type':'application/zip','X-Habita-Token':config.token,'X-File-Name':encodeURIComponent(file.name)},body:file});
    const data=await response.json();if(!response.ok)throw new Error(data.error);
    setFeed(data.feed);stale();message('Fuente GTFS actualizada. Ya puedes analizar una ubicación.',true);
  }catch(error){message(error.message);}finally{setBusy(false);$('feed-file').value='';}
});
$('load-path').addEventListener('click',async()=>{
  if(!$('feed-path').value.trim()){message('Ingresa la ruta de una carpeta GTFS o de un ZIP.');return;}
  clearMessage();setBusy(true,'Validando la fuente GTFS…');
  try{const data=await api('/api/feed/path',{path:$('feed-path').value.trim()});setFeed(data.feed);stale();message('Fuente GTFS actualizada.',true);}
  catch(error){message(error.message);}finally{setBusy(false);}
});
(async()=>{
  try{
    config=await api('/api/config');setFeed(config.feed);
    $('analyze').disabled=false;
    if(config.busy)message('Hay un análisis en curso en otra pestaña. Espera a que termine.');
  }catch(error){message('No se pudo conectar con HABITA. Ejecuta habita_gui.py y abre la dirección que muestra la terminal.');}
})();
