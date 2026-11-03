#!/usr/bin/env bash
# Verifica, na raiz do repositório, qual versão dos arquivos relevantes está em disco.
# Usa os mesmos hashes de blob do Git (git hash-object). Não altera nada.
set -u
ROOT="${1:-.}"
cd "$ROOT" || exit 1
declare -A REF=(
  ["HandSpace/app/src/main/java/com/handspace/interaction/SpatialWorldController.kt"]="9f7e6031e8c544ceb9a598f08f39b265c60cad79|bffa9fd3479dd2adf00ed3234bd10bd9ab4c3ef8|v0.4.1|v0.4"
  ["HandSpace/app/src/test/java/com/handspace/interaction/SpatialWorldControllerTest.kt"]="49bac738ad77c55feb18a3a75947a6e9f8757cba|20d092756dff48c0404ac1474fcf9952cab4a783|v0.4.1|v0.4"
  ["HandSpace/app/src/main/java/com/handspace/spatial/Camera3D.kt"]="7f401f3795d422f1c21f6bbfd69c2f9879748199|15930bab26b5eeae5fd2781886a8626e31689890|v0.4.1|v0.4"
  ["HandSpace/app/src/main/java/com/handspace/spatial/SpatialGeometry.kt"]="24aa358dcf6d1fd2f0ced8771893440133b592f2|b93e197e71d0c068f2ef6c3c1c2555764c25cbe5|v0.4.1|v0.4"
)
blob() {
  if git rev-parse --git-dir >/dev/null 2>&1; then git hash-object "$1"
  else python3 -c 'import hashlib,sys;d=open(sys.argv[1],"rb").read();print(hashlib.sha1(b"blob %d\0"%len(d)+d).hexdigest())' "$1"; fi
}
for f in "${!REF[@]}"; do
  IFS='|' read -r NEW OLD LNEW LOLD <<< "${REF[$f]}"
  if [ ! -f "$f" ]; then echo "AUSENTE  $f"; continue; fi
  h=$(blob "$f")
  if [ "$h" = "$NEW" ]; then echo "OK ($LNEW)  $f"
  elif [[ "$h" == "$OLD"* ]]; then echo "ANTIGO ($LOLD)  $f  <- o repositório ainda tem a versão anterior"
  else echo "DESCONHECIDO  $f  blob=$h"; fi
done
echo "--- arquivos com o nome SpatialWorldController.kt (deve aparecer apenas um):"
find . -name SpatialWorldController.kt -not -path "*/build/*" | sed 's/^/   /'
echo "--- marcadores no controlador:"
f="HandSpace/app/src/main/java/com/handspace/interaction/SpatialWorldController.kt"
[ -f "$f" ] && echo "   buttonCooldowns: $(grep -c buttonCooldowns "$f")   slot.cooldown: $(grep -c 'slot.cooldown' "$f")"
