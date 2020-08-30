#version 130

in vec3 position;
in vec3 color;

out vec3 passColor;

uniform mat4 eyeMatrix;

uniform mat4 transformationMatrix;

void main() {
    passColor = color;
    gl_Position = eyeMatrix* transformationMatrix * vec4(position, 1.0);
}